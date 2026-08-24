pipeline {
    agent {
        kubernetes {
            yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: maven
      image: maven:3.9.6-eclipse-temurin-17
      command: ['sleep']
      args: ['99d']
      resources:
        requests:
          cpu: "200m"
          memory: "400Mi"
        limits:
          cpu: "400m"
          memory: "700Mi"
      env:
        - name: MAVEN_OPTS
          value: "-Xms128m -Xmx384m"
    - name: aws-cli
      image: amazon/aws-cli:2.17.62
      command: ['sleep']
      args: ['99d']
      resources:
        requests:
          cpu: "100m"
          memory: "150Mi"
        limits:
          cpu: "200m"
          memory: "250Mi"
"""
        }
    }

    parameters {
        choice(name: 'BUILD_TYPE', choices: ['Quick Build', 'Full Build with Tests'], description: 'Choose build depth')
        booleanParam(name: 'RUN_TESTS', defaultValue: true, description: 'Run unit tests?')
        booleanParam(name: 'DEPLOY_TO_STAGING', defaultValue: false, description: 'Deploy to staging after build?')
        booleanParam(name: 'DEPLOY_TO_PROD', defaultValue: false, description: 'Deploy to production after build?')
        string(name: 'BRANCH_NAME', defaultValue: 'main', description: 'Branch to build')
    }

    options {
        timeout(time: 30, unit: 'MINUTES')               // prevents a stuck build from hogging a Spot node forever
        disableConcurrentBuilds()                        // avoids two builds racing on the same workspace
        buildDiscarder(logRotator(numToKeepStr: '20')) // keeps build history from growing unbounded
    }

    environment {
        ECR_REGISTRY      = "676278186770.dkr.ecr.us-west-2.amazonaws.com"
        ECR_REPO          = "676278186770.dkr.ecr.us-west-2.amazonaws.com/jenkins-eks-demo"
        AWS_REGION        = "us-west-2"
        S3_STAGING_BUCKET = "jenkins-eks-demo-staging-676278186770"
        CODEBUILD_PROJECT = "jenkins-eks-demo-image-build"
    }

    stages {
        stage('Checkout') {
            steps {
                container('maven') {
                    echo "Checking out branch: ${params.BRANCH_NAME}"
                    checkout scm
                    sh 'java -version'
                    sh 'mvn -version'
                }
            }
        }

        stage('Compile') {
            steps {
                container('maven') {
                    echo 'Compiling...'
                    sh 'mvn compile'
                }
            }
        }

        stage('Test') {
            when {
                expression { return params.RUN_TESTS }
            }
            steps {
                container('maven') {
                    echo 'Running tests...'
                    sh 'mvn test'
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Package') {
            steps {
                container('maven') {
                    echo 'Packaging...'
                    sh 'mvn package -DskipTests'
                }
            }
            post {
                success {
                    archiveArtifacts artifacts: 'target/*.jar', fingerprint: true, allowEmptyArchive: true
                }
            }
        }

        stage('Upload Artifacts to S3') {
            steps {
                container('aws-cli') {
                    sh """
                        echo "Renaming jar for staging..."
                        cp target/jenkins-eks-demo.jar app.jar

                        echo "Uploading app.jar and Dockerfile to S3..."
                        aws s3 cp app.jar s3://${S3_STAGING_BUCKET}/${BUILD_NUMBER}/app.jar
                        aws s3 cp Dockerfile s3://${S3_STAGING_BUCKET}/${BUILD_NUMBER}/Dockerfile

                        echo "Upload complete for build ${BUILD_NUMBER}"
                    """
                }
            }
        }

        stage('Trigger AWS CodeBuild') {
            steps {
                container('aws-cli') {
                    sh """
                        echo "Starting CodeBuild project ${CODEBUILD_PROJECT} for build ${BUILD_NUMBER}..."

                        aws codebuild start-build \
                            --project-name ${CODEBUILD_PROJECT} \
                            --environment-variables-override name=BUILD_ID,value=${BUILD_NUMBER},type=PLAINTEXT \
                            --region ${AWS_REGION} > codebuild-result.json

                        cat codebuild-result.json

                        BUILD_ID_STARTED=\$(cat codebuild-result.json | grep -o '"id": "[^"]*' | head -1 | cut -d'"' -f4)
                        echo "CodeBuild started: \$BUILD_ID_STARTED"
                    """
                }
            }
        }

        stage('Approval Gate') {
            when {
                expression { return params.DEPLOY_TO_STAGING || params.DEPLOY_TO_PROD }
            }
            steps {
                script {
                    def targetEnv = params.DEPLOY_TO_PROD ? "PRODUCTION" : "staging"
                    def userInput = input(
                        id: 'deployApproval',
                        message: "Deploy build #${BUILD_NUMBER} to ${targetEnv}?",
                        ok: 'Deploy',
                        submitter: 'admin,devops-team',   // restrict who can approve
                        parameters: [
                            choice(name: 'CONFIRM', choices: ['Yes - Deploy', 'No - Abort'], description: 'Confirm deployment')
                        ]
                    )
                    if (userInput != 'Yes - Deploy') {
                        error('Deployment aborted by approver.')
                    }
                }
            }
        }

        stage('Deploy to Staging') {
            when {
                expression { return params.DEPLOY_TO_STAGING && !params.DEPLOY_TO_PROD }
            }
            steps {
                lock('staging-deploy') {
                    container('maven') {
                        echo "Deploying build #${BUILD_NUMBER} to staging..."
                        sh "echo kubectl set image deployment/app-staging app=${ECR_REPO}:${BUILD_NUMBER} -n staging"
                    }
                }
            }
        }

        stage('Deploy to Production') {
            when {
                expression { return params.DEPLOY_TO_PROD }
            }
            steps {
                lock('production-deploy') {
                    container('maven') {
                        echo "Deploying build #${BUILD_NUMBER} to PRODUCTION..."
                        sh "echo kubectl set image deployment/app-prod app=${ECR_REPO}:${BUILD_NUMBER} -n production"
                    }
                }
            }
        }

        stage('Done') {
            steps {
                container('maven') {
                    sh '''
                        echo "===================================="
                        echo "Build complete on Spot instance"
                        echo "Node: $(hostname)"
                        echo "===================================="
                    '''
                }
            }
        }
    }

    post {
        success {
            echo " Build #${env.BUILD_NUMBER} successful. Spot agent terminating shortly."
        }
        failure {
            echo " Build #${env.BUILD_NUMBER} failed."
        }
        always {
            echo "Pipeline finished with status: ${currentBuild.currentResult}"
        }
    }
}
