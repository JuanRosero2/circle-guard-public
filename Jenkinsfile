// ============================================================
// Jenkinsfile — Pipeline declarativo para el ambiente cg-dev
// Se ejecuta automáticamente en cada push a la rama main/develop.
// Ejecuta tests unitarios, compila JARs, construye imágenes Docker
// y despliega en el namespace cg-dev de Kubernetes.
// Updated: Docker socket mounting configured for Jenkins
// ============================================================
pipeline {
    agent any

    environment {
        // Registry donde se publican las imágenes Docker
        DOCKER_REGISTRY = "docker.io/rosero007"
        // Tag de imagen basado en el short commit SHA para trazabilidad
        IMAGE_TAG       = "dev-${env.GIT_COMMIT?.take(8) ?: 'latest'}"
        // Namespace de Kubernetes para dev
        K8S_NAMESPACE   = "cg-dev"
        // Credencial de Docker configurada en Jenkins
        DOCKER_CREDS    = credentials('docker-hub-credentials')
    }

    stages {

        // -------------------------------------------------------
        // STAGE 1: Checkout
        // Clona el monorepo completo para tener build.gradle.kts
        // y todos los módulos disponibles.
        // -------------------------------------------------------
        stage('Checkout') {
            steps {
                checkout scm
                sh 'chmod +x gradlew'
            }
        }

        // -------------------------------------------------------
        // STAGE 2: Unit Tests (paralelo)
        // --continue permite que todos los módulos corran incluso
        // si uno falla, para obtener un reporte completo.
        // -------------------------------------------------------
        stage('Unit Tests') {
            parallel {
                stage('Test: auth-service') {
                    steps {
                        sh './gradlew :services:circleguard-auth-service:test --parallel --continue'
                    }
                    post {
                        always {
                            junit 'services/circleguard-auth-service/build/test-results/**/*.xml'
                        }
                    }
                }
                stage('Test: gateway-service') {
                    steps {
                        sh './gradlew :services:circleguard-gateway-service:test --parallel --continue --tests "*ServiceTest" --tests "*ControllerTest" --tests "*RepositoryTest"'
                    }
                    post {
                        always {
                            junit allowEmptyResults: true,
                                  testResults: 'services/circleguard-gateway-service/build/test-results/**/*.xml'
                        }
                    }
                }
                stage('Test: identity-service') {
                    steps {
                        sh './gradlew :services:circleguard-identity-service:test --parallel --continue'
                    }
                    post {
                        always {
                            junit allowEmptyResults: true,
                                  testResults: 'services/circleguard-identity-service/build/test-results/**/*.xml'
                        }
                    }
                }
                stage('Test: promotion-service') {
                    steps {
                        sh './gradlew :services:circleguard-promotion-service:test --parallel --continue --tests "*ServiceTest" --tests "*ListenerTest"'
                    }
                    post {
                        always {
                            junit 'services/circleguard-promotion-service/build/test-results/**/*.xml'
                        }
                    }
                }
                stage('Test: notification-service') {
                    steps {
                        sh './gradlew :services:circleguard-notification-service:test --parallel --continue'
                    }
                    post {
                        always {
                            junit 'services/circleguard-notification-service/build/test-results/**/*.xml'
                        }
                    }
                }
                stage('Test: form-service') {
                    steps {
                        sh './gradlew :services:circleguard-form-service:test --parallel --continue'
                    }
                    post {
                        always {
                            junit 'services/circleguard-form-service/build/test-results/**/*.xml'
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------
        // STAGE 3: Build JARs (paralelo)
        // Compila los bootJar de los 6 servicios en paralelo.
        // -x test evita re-ejecutar tests ya corridos arriba.
        // -------------------------------------------------------
        stage('Build JARs') {
            parallel {
                stage('JAR: auth-service') {
                    steps {
                        sh './gradlew :services:circleguard-auth-service:bootJar --parallel -x test --no-daemon'
                    }
                }
                stage('JAR: gateway-service') {
                    steps {
                        sh './gradlew :services:circleguard-gateway-service:bootJar --parallel -x test --no-daemon'
                    }
                }
                stage('JAR: identity-service') {
                    steps {
                        sh './gradlew :services:circleguard-identity-service:bootJar --parallel -x test --no-daemon'
                    }
                }
                stage('JAR: promotion-service') {
                    steps {
                        sh './gradlew :services:circleguard-promotion-service:bootJar --parallel -x test --no-daemon'
                    }
                }
                stage('JAR: notification-service') {
                    steps {
                        sh './gradlew :services:circleguard-notification-service:bootJar --parallel -x test --no-daemon'
                    }
                }
                stage('JAR: form-service') {
                    steps {
                        sh './gradlew :services:circleguard-form-service:bootJar --parallel -x test --no-daemon'
                    }
                }
            }
        }

        // -------------------------------------------------------
        // STAGE 4: Docker Build & Push (secuencial)
        // El contexto de build es SIEMPRE el punto (.) = raíz del repo.
        // Se usa -f para apuntar al Dockerfile del servicio específico.
        // -------------------------------------------------------
        stage('Docker Build & Push') {
            steps {
                script {
                    def services = ['auth-service', 'gateway-service', 'identity-service', 'promotion-service', 'notification-service', 'form-service']
                    
                    // Login to Docker Hub once
                    withCredentials([usernamePassword(credentialsId: 'docker-hub-credentials', usernameVariable: 'DOCKER_USR', passwordVariable: 'DOCKER_PSW')]) {
                        sh """
                            echo \$DOCKER_PSW | docker login -u \$DOCKER_USR --password-stdin
                        """
                    }
                    
                    // Build and push images sequentially to avoid resource conflicts
                    services.each { service ->
                        stage("Docker: ${service}") {
                            steps {
                                sh """
                                    echo "Building and pushing ${service}..."
                                    docker build -f services/circleguard-${service}/Dockerfile \
                                        -t ${DOCKER_REGISTRY}/circleguard/${service}:${IMAGE_TAG} .
                                    docker push ${DOCKER_REGISTRY}/circleguard/${service}:${IMAGE_TAG}
                                    echo "Successfully built and pushed ${service}"
                                """
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------
        // STAGE 5: Deploy to cg-dev
        // Actualiza la imagen en el Deployment de K8s y espera que
        // el rollout termine OK antes de continuar.
        // -------------------------------------------------------
        stage('Deploy to cg-dev') {
            steps {
                withKubeConfig([credentialsId: 'KUBECONFIG']) {
                    script {
                        def services = [
                            'auth-service',
                            'gateway-service',
                            'identity-service',
                            'promotion-service',
                            'notification-service',
                            'form-service'
                        ]
                        // Aplicar los manifiestos de configuración primero
                        sh "kubectl apply -f k8s/infra/ -n ${K8S_NAMESPACE}"
                        sh "kubectl apply -f k8s/dev/ --recursive"

                        // Actualizar la imagen de cada Deployment con la nueva tag
                        services.each { svc ->
                            sh """
                                kubectl set image deployment/${svc} \
                                    ${svc}=${DOCKER_REGISTRY}/circleguard/${svc}:${IMAGE_TAG} \
                                    -n ${K8S_NAMESPACE}
                            """
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------
        // STAGE 6: Rollout Status
        // Verifica que todos los pods estén Running antes de hacer
        // el smoke test.
        // -------------------------------------------------------
        stage('Rollout Status') {
            steps {
                withKubeConfig([credentialsId: 'KUBECONFIG']) {
                    script {
                        def services = [
                            'auth-service', 'gateway-service', 'identity-service',
                            'promotion-service', 'notification-service', 'form-service'
                        ]
                        services.each { svc ->
                            sh "kubectl rollout status deployment/${svc} -n ${K8S_NAMESPACE} --timeout=120s"
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------
        // STAGE 7: Smoke Test
        // Verifica que el gateway responda 200 OK en /actuator/health
        // como proxy de que el sistema arrancó correctamente.
        // -------------------------------------------------------
        stage('Smoke Test') {
            steps {
                withKubeConfig([credentialsId: 'KUBECONFIG']) {
                    sh """
                        # Esperar que el gateway esté listo antes de testear
                        sleep 10
                        GATEWAY_URL=\$(kubectl get svc gateway-service -n ${K8S_NAMESPACE} \
                            -o jsonpath='{.spec.clusterIP}')
                        curl -f http://\${GATEWAY_URL}:8087/actuator/health || \
                            (echo 'Smoke test FAILED' && exit 1)
                        echo 'Smoke test PASSED'
                    """
                }
            }
        }
    }

    post {
    always {
        // Solo intenta publicar si la carpeta de reportes existe
        script {
            if (fileExists('build/reports')) {
                publishHTML(target: [
                    allowMissing: true,
                    reportDir: 'build/reports',
                    reportFiles: 'index.html',
                    reportName: 'Test Report'
                ])
            }
        }
    }
}
}
