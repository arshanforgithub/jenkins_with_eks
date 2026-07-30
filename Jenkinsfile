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
"""
        }
    }

    
    parameters {
        choice(name: 'BUILD_TYPE', choices: ['Quick Build', 'Full Build with Tests'], description: 'Choose build depth')
        booleanParam(name: 'RUN_TESTS', defaultValue: true, description: 'Run unit tests?')
        booleanParam(name: 'DEPLOY_TO_STAGING', defaultValue: false, description: 'Deploy to staging after build?')
        string(name: 'BRANCH_NAME', defaultValue: 'main', description: 'Branch to build')
    }

    options {
        timeout(time: 30, unit: 'MINUTES')      // prevents a stuck build from hogging a Spot node forever
        disableConcurrentBuilds()                // avoids two builds racing on the same workspace
        buildDiscarder(logRotator(numToKeepStr: '20')) // keeps build history from growing unbounded
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

        
        stage('Approval Gate') {
            when {
                expression { return params.DEPLOY_TO_STAGING }
            }
            steps {
                script {
                    def userInput = input(
                        id: 'deployApproval',
                        message: 'Deploy this build to staging?',
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
                expression { return params.DEPLOY_TO_STAGING }
            }
            steps {
                container('maven') {
                    echo 'Deploying to staging environment...'
                    sh 'echo "kubectl apply / helm upgrade goes here"'
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
