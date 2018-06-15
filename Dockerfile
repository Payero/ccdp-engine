FROM ubuntu:latest
RUN apt-get update  
RUN apt-get install -y default-jre
RUN apt-get install -y vim
RUN apt-get install -y python
RUN apt-get install -y python-pip
RUN apt-get install -y net-tools
RUN apt-get install -y curl
RUN pip install boto3

ENV AWS_ACCESS_KEY_ID AKIAILDTHAKOE7G3SFGA
ENV AWS_SECRET_ACCESS_KEY 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
ENV AWS_DEFAULT_REGION us-east-1
ENV AWS_DEFAULT_OUTPUT json
ENV CCDP_HOME /data/ccdp/ccdp-engine

WORKDIR /data/ccdp 
VOLUME /data/ccdp


# RUN /data/ccdp/ccdp_install.py -a download -d s3://ccdp-settings/ccdp-engine.tgz -t /data/ccdp
# ENTRYPOINT /data/ccdp/ccdp-engine/bin/ccdp_agent.sh start

