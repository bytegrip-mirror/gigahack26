pipeline {
    agent {
        docker {
            image 'docker:24.0-cli'
            args '-v /var/run/docker.sock:/var/run/docker.sock -u root'
        }
    }

    environment {
        API_IMAGE = "gigahack26-api:latest"
        API_CONTAINER = "gigahack26-api"
        API_DOMAIN = "hackapi.pistonpunk.com"
        API_PORT = "8080"

        UI_IMAGE = "gigahack26-ui:latest"
        UI_CONTAINER = "gigahack26-ui"
        UI_DOMAIN = "hack.pistonpunk.com"
        UI_PORT = "80"

        DB_IMAGE = "postgres:16-alpine"
        DB_CONTAINER = "gigahack26-db"
        DB_NAME = "gigahack26"
        DB_USER = "gigahack"

        MONGO_IMAGE = "mongo:7.0"
        MONGO_CONTAINER = "gigahack26-mongo"
        MONGO_DB_NAME = "gigahack26"
        MONGO_USER = "gigahack"

        TRAEFIK_ENTRYPOINT = "websecure"
        TRAEFIK_CERTRESOLVER = "myresolver"
        NETWORK = "web"
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main',
                    url: 'git@github.com:pistonpunk/gigahack26.git',
                    credentialsId: 'gigahack26-ssh'
            }
        }

        stage('Test API') {
            steps {
                sh '''
                    docker run --rm \
                        -v "$(pwd)/backend":/src \
                        -v gigahack26-maven-cache:/root/.m2 \
                        -w /src \
                        maven:3.9-eclipse-temurin-21 \
                        mvn -B -ntp test
                '''
            }
        }

        stage('Check UI') {
            steps {
                sh '''
                    docker run --rm \
                        -v "$(pwd)/frontend":/src \
                        -v gigahack26-node-modules:/src/node_modules \
                        -w /src \
                        node:22-alpine \
                        sh -c "npm ci && npm run check"
                '''
            }
        }

        stage('Build API Image') {
            steps {
                sh "docker build -t ${API_IMAGE} ./backend"
            }
        }

        stage('Build UI Image') {
            steps {
                sh "docker build --build-arg PUBLIC_API_URL=https://${API_DOMAIN} -t ${UI_IMAGE} ./frontend"
            }
        }

        stage('Deploy PostgreSQL') {
            steps {
                withCredentials([string(credentialsId: 'gigahack26-db-password', variable: 'DB_PASSWORD')]) {
                    sh 'docker rm -f ${DB_CONTAINER} || true'
                    sh '''
                        docker run -d \
                            --name ${DB_CONTAINER} \
                            -e POSTGRES_USER=${DB_USER} \
                            -e POSTGRES_PASSWORD=${DB_PASSWORD} \
                            -e POSTGRES_DB=${DB_NAME} \
                            -v gigahack26-postgres-data:/var/lib/postgresql/data \
                            --network=${NETWORK} \
                            --restart unless-stopped \
                            ${DB_IMAGE}
                    '''
                    sh '''
                        for attempt in $(seq 1 30); do
                            if docker exec ${DB_CONTAINER} pg_isready -U ${DB_USER} -d ${DB_NAME} >/dev/null 2>&1; then
                                echo "PostgreSQL is ready"
                                exit 0
                            fi
                            sleep 2
                        done
                        echo "PostgreSQL did not become ready in time"
                        docker logs ${DB_CONTAINER} || true
                        exit 1
                    '''
                }
            }
        }

        stage('Deploy MongoDB') {
            steps {
                withCredentials([string(credentialsId: 'gigahack26-mongo-password', variable: 'MONGO_PASSWORD')]) {
                    sh 'docker rm -f ${MONGO_CONTAINER} || true'
                    sh '''
                        docker run -d \
                            --name ${MONGO_CONTAINER} \
                            -e MONGO_INITDB_ROOT_USERNAME=${MONGO_USER} \
                            -e MONGO_INITDB_ROOT_PASSWORD=${MONGO_PASSWORD} \
                            -e MONGO_INITDB_DATABASE=${MONGO_DB_NAME} \
                            -v gigahack26-mongo-data:/data/db \
                            --network=${NETWORK} \
                            --restart unless-stopped \
                            ${MONGO_IMAGE}
                    '''
                    sh '''
                        for attempt in $(seq 1 30); do
                            if docker exec ${MONGO_CONTAINER} mongosh --quiet \
                                --username ${MONGO_USER} \
                                --password ${MONGO_PASSWORD} \
                                --authenticationDatabase admin \
                                --eval "db.adminCommand('ping').ok" >/dev/null 2>&1; then
                                echo "MongoDB is ready"
                                exit 0
                            fi
                            sleep 2
                        done
                        echo "MongoDB did not become ready in time"
                        docker logs ${MONGO_CONTAINER} || true
                        exit 1
                    '''
                }
            }
        }

        stage('Run containers') {
            steps {
                withCredentials([
                    string(credentialsId: 'gigahack26-db-password', variable: 'DB_PASSWORD'),
                    string(credentialsId: 'gigahack26-mongo-password', variable: 'MONGO_PASSWORD')
                ]) {
                    sh 'docker rm -f ${API_CONTAINER} || true'
                    sh 'docker rm -f ${UI_CONTAINER} || true'

                    sh '''
                        docker run -d \
                            --name ${API_CONTAINER} \
                            -e SERVER_PORT=${API_PORT} \
                            -e POSTGRES_URL=jdbc:postgresql://${DB_CONTAINER}:5432/${DB_NAME} \
                            -e POSTGRES_USER=${DB_USER} \
                            -e POSTGRES_PASSWORD=${DB_PASSWORD} \
                            -e MONGO_URI=mongodb://${MONGO_USER}:${MONGO_PASSWORD}@${MONGO_CONTAINER}:27017/${MONGO_DB_NAME}?authSource=admin \
                            -e CORS_ALLOWED_ORIGINS=https://${UI_DOMAIN} \
                            --label traefik.enable=true \
                            --label "traefik.docker.network=${NETWORK}" \
                            --label "traefik.http.routers.gigahack26api.rule=Host(\\`${API_DOMAIN}\\`)" \
                            --label "traefik.http.routers.gigahack26api.entrypoints=${TRAEFIK_ENTRYPOINT}" \
                            --label "traefik.http.routers.gigahack26api.tls.certresolver=${TRAEFIK_CERTRESOLVER}" \
                            --label "traefik.http.services.gigahack26api.loadbalancer.server.port=${API_PORT}" \
                            --network=${NETWORK} \
                            --restart unless-stopped \
                            ${API_IMAGE}
                    '''

                    sh '''
                        docker run -d \
                            --name ${UI_CONTAINER} \
                            --label traefik.enable=true \
                            --label "traefik.docker.network=${NETWORK}" \
                            --label "traefik.http.routers.gigahack26ui.rule=Host(\\`${UI_DOMAIN}\\`)" \
                            --label "traefik.http.routers.gigahack26ui.entrypoints=${TRAEFIK_ENTRYPOINT}" \
                            --label "traefik.http.routers.gigahack26ui.tls.certresolver=${TRAEFIK_CERTRESOLVER}" \
                            --label "traefik.http.services.gigahack26ui.loadbalancer.server.port=${UI_PORT}" \
                            --network=${NETWORK} \
                            --restart unless-stopped \
                            ${UI_IMAGE}
                    '''
                }
            }
        }

        stage('Verify') {
            steps {
                script {
                    def ready = false
                    for (int attempt = 0; attempt < 30; attempt++) {
                        def probe = sh(
                            script: "docker exec ${API_CONTAINER} wget -q -O - http://localhost:${API_PORT}/api/status || true",
                            returnStdout: true
                        ).trim()
                        if (probe.contains('"postgres":"up"') && probe.contains('"mongo":"up"')) {
                            echo "API status: ${probe}"
                            ready = true
                            break
                        }
                        sleep(time: 2, unit: 'SECONDS')
                    }

                    if (!ready) {
                        sh "docker logs ${API_CONTAINER} --tail 120 || true"
                        error('The API never reported both stores as up.')
                    }

                    def ui = sh(
                        script: "docker exec ${UI_CONTAINER} wget -q -O - http://localhost:${UI_PORT}/ | head -c 200 || true",
                        returnStdout: true
                    ).trim()
                    if (!ui.contains('<!doctype html>')) {
                        sh "docker logs ${UI_CONTAINER} --tail 120 || true"
                        error('The UI container did not serve index.html.')
                    }
                    echo "UI is serving the app shell."
                }
            }
        }
    }

    post {
        success {
            echo "Frontend deployed to https://${UI_DOMAIN}"
            echo "API deployed to https://${API_DOMAIN}"
        }
        failure {
            echo "Deployment failed."
            sh 'docker logs ${API_CONTAINER} --tail 120 || true'
        }
    }
}
