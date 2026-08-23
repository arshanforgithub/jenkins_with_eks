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
      volumeMounts:
        - name: docker-config
          mountPath: /docker-config
    - name: buildkit
      image: moby/buildkit:master-rootless
      args: ["--oci-worker-no-process-sandbox"]
      securityContext:
        seccompProfile:
          type: Unconfined
        runAsUser: 1000
        runAsGroup: 1000
      resources:
        requests:
          cpu: "300m"
          memory: "400Mi"
        limits:
          cpu: "800m"
          memory: "1Gi"
      env:
        - name: DOCKER_CONFIG
          value: /docker-config
        - name: BUILDKIT_HOST
          value: unix:///run/user/1000/buildkit/buildkitd.sock
      volumeMounts:
        - name: docker-config
          mountPath: /docker-config
  volumes:
    - name: docker-config
      emptyDir: {}
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
        timeout(time: 30, unit: 'MINUTES')      // prevents a stuck build from hogging a Spot node forever
        disableConcurrentBuilds()                // avoids two builds racing on the same workspace
        buildDiscarder(logRotator(numToKeepStr: '20')) // keeps build history from growing unbounded
    }

    environment {
        ECR_REGISTRY = "6XXXXXXX0.dkr.ecr.us-west-2.amazonaws.com"
        ECR_REPO     = "6XXXXXXX0.dkr.ecr.us-west-2.amazonaws.com/jenkins-eks-demo"
        AWS_REGION   = "us-west-2"
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

        stage('ECR Login') {
            steps {
                container('aws-cli') {
                    // Generates a short-lived ECR auth token using the Spot node's IAM role
                    // and writes it as a docker-style config.json onto the shared volume,
                    // so the BuildKit container (which has no AWS CLI) can push using it.
                    sh """
                        mkdir -p /docker-config
                        TOKEN=\$(aws ecr get-login-password --region ${AWS_REGION})
                        AUTH=\$(echo -n "AWS:\$TOKEN" | base64 -w0)
                        cat > /docker-config/config.json <<EOF
{"auths":{"${ECR_REGISTRY}":{"auth":"\$AUTH"}}}
EOF
                    """
                }
            }
        }

        stage('Build & Push Docker Image') {
            steps {
                container('buildkit') {
                    echo "Building and pushing image: ${ECR_REPO}:${BUILD_NUMBER}"
                    sh """
                        buildctl build \
                          --frontend dockerfile.v0 \
                          --local context=. \
                          --local dockerfile=. \
                          --output type=image,name=${ECR_REPO}:${BUILD_NUMBER},${ECR_REPO}:latest,push=true
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
            // Slack/email notification - requires Slack plugin + configured webhook credential
            // slackSend(channel: '#builds', color: 'good', message: " Build ${env.BUILD_NUMBER} succeeded: ${env.BUILD_URL}")
        }
        failure {
            echo " Build #${env.BUILD_NUMBER} failed."
            // slackSend(channel: '#builds', color: 'danger', message: " Build ${env.BUILD_NUMBER} failed: ${env.BUILD_URL}")
        }
        always {
            echo "Pipeline finished with status: ${currentBuild.currentResult}"
        }
    }
}
