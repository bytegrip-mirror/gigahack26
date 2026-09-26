pipeline {
    agent {
        docker {
            image 'docker:24.0-cli'
            args '-v /var/run/docker.sock:/var/run/docker.sock -u root'
        }
    }

    options {
        disableConcurrentBuilds()
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

        REDIS_IMAGE = "redis:7-alpine"
        REDIS_CONTAINER = "gigahack26-redis"

        MONGO_IMAGE = "mongo:7.0"
        MONGO_CONTAINER = "gigahack26-mongo"
        MONGO_DB_NAME = "gigahack26"
        MONGO_USER = "gigahack"

        TRAEFIK_ENTRYPOINT = "websecure"
        TRAEFIK_CERTRESOLVER = "myresolver"
        NETWORK = "web"

        CLASSIFIER_ENGINE = "local"
        DEEPSEEK_MODEL = "deepseek-flash"
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
                        -v "$(pwd)/api":/src \
                        -v gigahack26-gradle-cache:/root/.gradle \
                        -w /src \
                        eclipse-temurin:25-jdk-alpine \
                        ./gradlew --no-daemon test
                '''
            }
        }

        stage('Check UI') {
            steps {
                sh '''
                    docker run --rm \
                        -v "$(pwd)/ui":/src \
                        -v gigahack26-ui-pnpm-modules:/src/node_modules \
                        -w /src \
                        node:22-alpine \
                        sh -c "corepack enable && CI=true pnpm install --frozen-lockfile && pnpm run check"
                '''
            }
        }

        stage('Build API Image') {
            steps {
                sh "docker build -t ${API_IMAGE} ./api"
            }
        }

        stage('Build UI Image') {
            steps {
                sh "docker build --build-arg PUBLIC_API_URL=https://${API_DOMAIN} -t ${UI_IMAGE} ./ui"
            }
        }

        stage('Deploy PostgreSQL') {
            steps {
                withCredentials([string(credentialsId: 'gigahack26-db-password', variable: 'DB_PASSWORD')]) {
                    sh '''
                        running_image=$(docker inspect -f '{{.Config.Image}}' ${DB_CONTAINER} 2>/dev/null || echo none)
                        if [ "$running_image" = "${DB_IMAGE}" ]; then
                            echo "Reusing ${DB_CONTAINER} (image unchanged); data volume left in place"
                            docker start ${DB_CONTAINER} >/dev/null
                        else
                            echo "Recreating ${DB_CONTAINER} (${running_image} -> ${DB_IMAGE}); the data volume is preserved"
                            docker rm -f ${DB_CONTAINER} >/dev/null 2>&1 || true
                            docker run -d \
                                --name ${DB_CONTAINER} \
                                -e POSTGRES_USER=${DB_USER} \
                                -e POSTGRES_PASSWORD=${DB_PASSWORD} \
                                -e POSTGRES_DB=${DB_NAME} \
                                -v gigahack26-postgres-data:/var/lib/postgresql/data \
                                --network=${NETWORK} \
                                --restart unless-stopped \
                                ${DB_IMAGE}
                        fi
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
                    sh '''
                        running_image=$(docker inspect -f '{{.Config.Image}}' ${MONGO_CONTAINER} 2>/dev/null || echo none)
                        if [ "$running_image" = "${MONGO_IMAGE}" ]; then
                            echo "Reusing ${MONGO_CONTAINER} (image unchanged); data volume left in place"
                            docker start ${MONGO_CONTAINER} >/dev/null
                        else
                            echo "Recreating ${MONGO_CONTAINER} (${running_image} -> ${MONGO_IMAGE}); the data volume is preserved"
                            docker rm -f ${MONGO_CONTAINER} >/dev/null 2>&1 || true
                            docker run -d \
                                --name ${MONGO_CONTAINER} \
                                -e MONGO_INITDB_ROOT_USERNAME=${MONGO_USER} \
                                -e MONGO_INITDB_ROOT_PASSWORD=${MONGO_PASSWORD} \
                                -e MONGO_INITDB_DATABASE=${MONGO_DB_NAME} \
                                -v gigahack26-mongo-data:/data/db \
                                --network=${NETWORK} \
                                --restart unless-stopped \
                                ${MONGO_IMAGE}
                        fi
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

        stage('Deploy Redis') {
            steps {
                sh '''
                    running_image=$(docker inspect -f '{{.Config.Image}}' ${REDIS_CONTAINER} 2>/dev/null || echo none)
                    if [ "$running_image" = "${REDIS_IMAGE}" ]; then
                        echo "Reusing ${REDIS_CONTAINER} (image unchanged)"
                        docker start ${REDIS_CONTAINER} >/dev/null
                    else
                        echo "Recreating ${REDIS_CONTAINER} (${running_image} -> ${REDIS_IMAGE})"
                        docker rm -f ${REDIS_CONTAINER} >/dev/null 2>&1 || true
                        docker run -d \
                            --name ${REDIS_CONTAINER} \
                            --network=${NETWORK} \
                            --restart unless-stopped \
                            ${REDIS_IMAGE} \
                            redis-server --save '' --appendonly no --maxmemory 256mb --maxmemory-policy allkeys-lru
                    fi
                '''
                sh '''
                    for attempt in $(seq 1 20); do
                        if docker exec ${REDIS_CONTAINER} redis-cli ping 2>/dev/null | grep -q PONG; then
                            echo "Redis is ready"
                            exit 0
                        fi
                        sleep 2
                    done
                    echo "Redis did not become ready in time"
                    docker logs ${REDIS_CONTAINER} || true
                    exit 1
                '''
            }
        }

        stage('Run containers') {
            steps {
                withCredentials([
                    string(credentialsId: 'gigahack26-db-password', variable: 'DB_PASSWORD'),
                    string(credentialsId: 'gigahack26-mongo-password', variable: 'MONGO_PASSWORD'),
                    string(credentialsId: 'gigahack26-deepseek-key', variable: 'DEEPSEEK_API_KEY')
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
                            -e CLASSIFIER_ENGINE=${CLASSIFIER_ENGINE} \
                            -e DEEPSEEK_API_KEY=${DEEPSEEK_API_KEY:-} \
                            -e DEEPSEEK_MODEL=${DEEPSEEK_MODEL} \
                            -e CORPUS_SEED=true \
                            -e ADMIN_ENABLED=false \
                            -e REDIS_HOST=${REDIS_CONTAINER} \
                            -e REDIS_PORT=6379 \
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

        stage('Verify the deployment') {
            steps {
                sh '''
                    for attempt in $(seq 1 90); do
                        status=$(docker exec ${API_CONTAINER} wget -q -O - http://localhost:8080/api/status 2>/dev/null || echo "")
                        if echo "$status" | grep -q '"postgres":"up"' && echo "$status" | grep -q '"mongo":"up"'; then
                            echo "$status"
                            exit 0
                        fi
                        sleep 2
                    done
                    echo "The API did not report both stores up in time"
                    docker logs ${API_CONTAINER} --tail 120 || true
                    exit 1
                '''
                sh '''
                    for attempt in $(seq 1 30); do
                        if docker exec ${API_CONTAINER} wget -q -O - "https://${UI_DOMAIN}/" 2>/dev/null \
                            | grep -qi "<!doctype html>"; then
                            echo "The board answers on https://${UI_DOMAIN}"
                            exit 0
                        fi
                        sleep 2
                    done
                    echo "The board did not answer on https://${UI_DOMAIN}"
                    exit 1
                '''
                sh '''
                    set -eu
                    fail() {
                        echo "$1"
                        docker logs ${API_CONTAINER} --tail 200 || true
                        exit 1
                    }
                    status=$(docker exec ${API_CONTAINER} wget -q -O - "https://${API_DOMAIN}/api/status") || fail "status did not answer"
                    echo "$status"

                    for attempt in 1 2 3; do
                        board=$(docker exec ${API_CONTAINER} wget -q -O - "https://${API_DOMAIN}/api/companies") \
                            || fail "the board payload did not answer — status alone can be green while reads fail"
                        case "$board" in
                            \[*\]) ;;
                            *) fail "the board payload is not a JSON array: $(echo "$board" | head -c 200)" ;;
                        esac
                        case "$board" in
                            \[\]) fail "the board payload is empty" ;;
                        esac
                        sleep 2
                    done

                    meta=$(docker exec ${API_CONTAINER} wget -q -O - "https://${API_DOMAIN}/api/meta?serviceLine=Intelligent%20Automation") \
                        || fail "meta did not answer"
                    case "$meta" in
                        *bands*) ;;
                        *) fail "meta carries no bands: $(echo "$meta" | head -c 200)" ;;
                    esac

                    settings=$(docker exec ${API_CONTAINER} wget -q -O - "https://${API_DOMAIN}/api/settings") \
                        || fail "settings did not answer"
                    case "$settings" in
                        *baseRate*) ;;
                        *) fail "settings did not carry the scoring profile: $(echo "$settings" | head -c 200)" ;;
                    esac

                    rows=$(echo "$board" | tr ',' '\\n' | grep -c '"id"' || true)
                    echo "--- api log, last 40 lines ---"
                    docker logs ${API_CONTAINER} --tail 40 2>&1 || true
                    echo "--- end of api log ---"
                    echo
                    echo "The API answers on https://${API_DOMAIN} through Traefik, over TLS: $rows company rows, meta and settings read"
                '''
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
