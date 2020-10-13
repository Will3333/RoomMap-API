FROM openjdk:8-jre

WORKDIR /root

RUN curl https://github.com/Will3333/RoomMap-API/releases/download/v0.1.0-beta/roommap-api-0.1.0-beta.tar && \
    tar -xf roommap-api-0.1.0-beta.tar -C roommap-api && rm -f roommap-api-0.1.0-beta.tar

WORKDIR /root/roommap-api

EXPOSE 80

CMD ["bin/roommap-api", "--config-file /data/roommap-api.yml"]