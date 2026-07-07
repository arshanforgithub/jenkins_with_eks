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
          cpu: "300m"
          memory: "768Mi"
        limits:
          cpu: "600m"
          memory: "1536Mi"
      env:
        - name: MAVEN_OPTS
          value: "-Xms256m -Xmx512m"
"""
        }
    }

    stages {
        stage('Checkout') {
            steps {
                container('maven') {
                    echo 'Checking out code...'
                    sh 'java -version'
                    sh 'mvn -version'
                }
            }
        }
        stage('Compile') {
            steps {
                container('maven') {
                    echo 'Compiling...'
                    sh 'echo Compilation successful'
                }
            }
        }
        stage('Test') {
            steps {
                container('maven') {
                    echo 'Running unit tests...'
                    sh 'echo All tests passed'
                }
            }
        }
        stage('Package') {
            steps {
                container('maven') {
                    echo 'Packaging application...'
                    sh 'echo JAR created successfully'
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
            echo "Build successful. Spot agent terminating shortly."
        }
        failure {
            echo "Build failed. Check logs above."
        }
    }
}
