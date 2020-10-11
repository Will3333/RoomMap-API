FROM openjdk:8-jre

# Download of a release
# Extract, move and clean

EXPOSE 80

CMD ["bin/roommap-api", "--config-file .roommap-api.yml"]