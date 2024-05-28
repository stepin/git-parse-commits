#NOTE: jc requires gcc and there is no package for alpine, that's why it's not alpine base image
FROM python:3

RUN pip3 install jc \
&& apt-get update \
&& apt-get install -y jq \
&& rm -rf /var/lib/apt/lists/* \
&& mkdir /app

WORKDIR /app

COPY git-parse-commits ./

ENTRYPOINT ["/app/git-parse-commits"]
