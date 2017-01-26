FROM python:2.7.13
RUN apt-get -y update

COPY src /tmp/

VOLUME ["/etc/marathon/connf", "/etc/m"]

ENTRYPOINT ["python", "/tmp/mesos_config.py"]

#CMD [""] 