FROM openjdk:8-jre

WORKDIR /root

RUN curl -L https://github.com/Will3333/RoomMap-API/releases/download/v0.1.0-beta/roommap-api-0.1.0-beta.tar --output roommap-api.tar && \
    tar -xvf roommap-api.tar && rm -f roommap-api.tar && \
    mv roommap-api-0.1.0-beta roommap-api

WORKDIR /root/roommap-api

EXPOSE 80

CMD bin/roommap-api -f /data/roommap-api.yml