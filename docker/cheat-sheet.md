# General Docker Information and Testing

## Docker
For installation just follow any of the multiple tutorials out there.  This document will emphasize just on a hanfful number of commands used for testing and working with docker containers.

### Post-Install Changes
Docker engine allows you to change its overall behavior by creating and modifying `/etc/docker/daemon.json`.  CCDP uses a docker client to communicate with the docker engine and therefore the docker engine needs to expose the API.  This done by setting the host where the engine is running:
```json
{
  "debug": true,
  "graph": "/storage/docker",
  "hosts": [
      "unix:///var/run/docker.sock",
      "tcp://172.17.0.1:2375"]
}
```
After this file is modified you need to do `service docker restart`

### Docker Resource Files Information
Docker uses cgroup to assign resources to containers and groups them together based on their names.  The host running the docker engine will have all that information in the `sys/fs/cgroup/memory|cpuacct/docker` directory.  Inside each of those folders you will find subdirectories matching the containers ids.


### Docker Commands

#### `docker version`
Allows you to verify docker was installed properly as well as to check the version you are running.

#### `docker ps`
Used to check which containers are running.  By default only shows the one running so to see them all you need to add `-a`.

#### `docker system prune -a`
It removes all ununsed images and containers from the host computer.  Great to clean and save a lot of hard drive space

#### `docker stats`
Allows you to see the system's performance by displaying resource utilization for each container.  It monitors the containers continuoulsy unles the `--no-stream` option is passed.  You can also format the output with the `--format` as shown below.

`docker stats --no-stream --format '{"container": "{{.Container}}","container-name": "{{.Name}}","container-id": "{{.ID}}","container-cpu": "{{.CPUPerc}}","container-mem": "{{.MemUsage}}","container-net": "{{.NetIO}}","container-block": "{{.BlockIO}}","container-mem-perc": "{{.MemPerc}}","container-pids": "{{.PIDs}}"}'
`
This will return a JSON dictionary with resources used by the containers running at that time.

#### Building and running a container from a Dockerfile
Dockerfile allows you to automatically get an image, create a container, modify its behavior, and run it.  For example a file with the listing below creates a container from ubuntu:latest and runs `apt-get update` and `apt-get install`.  Sets the environment variable so it can be used inside the container and uses `/data/docker/examples/curl` as the working directory in the host computer.  It creates a volume by mapping the `/data/docker/examples/curl` from the host computer as `/data` in the container.  Once all this is done it runs the CMD when the container starts

```docker
FROM ubuntu:latest  
RUN apt-get update  
RUN apt-get install --no-install-recommends --no-install-suggests -y curl  
ENV SITE_URL https://google.com/  
WORKDIR /data/docker/examples/curl  
VOLUME /data/docker/examples/curl:/data
CMD sh -c "curl -L $SITE_URL > /data/results"
```


#### `docker run <options> <image name> `
Creates a container from a specific image and runs it.  There are many options here, but here are the most essential ones:
- `-d`: runs the container as daemon 
- `--name`: gives the container a name rather than a random generated one
- `-p container:host`: maps a port from the container to the host
- `-v`: maps a volume or a directory from the host to the container and also allows you to set the permissions.  For instance `-v /my/dir/html:/usr/share/nginx/html:ro` mounts the `/my/dir/html` as `/usr/share/nginx/html` making the direcotry read only.

The following command creates a new container running as daemon (-d) called test-nginx (--name) and maps a port from the container (80) as 5555 in the local host.  It also maps a volume (-v) from the localhost as /usr/share/nginx/html and makes it read only (:ro) from the image called nignx:latest

`docker run -d --name test-nginx -p 5555:80 -v $(pwd):/usr/share/nginx/html:ro nginx:latest`


One thing to keep in mind with run is that if you modify the entrypoint from the default bash to something else the arguments passed to the entry point are set after the name of the container.
##### Example:
- Running a container with a set memory size

`docker run -it --memory=512mb --rm -v /src/vol:/tgt/vol <image> bash`


#### `docker inspect <container id>`
Used to get details about how the container is running.  It returns a long JSON object with more details you will ever know what to do with them

#### `docker exec -it <container id> bash`
The exec command allows you to attach or enter to a running container.  This is very useful if you want to see what the container is doing.  


## CCDP Docker Testing and information
CCDP uses a modified Centos-7 image (payero:centos-7:ccdp).  Assumming the host has a `/data/ccdp` directory the foloowing is a list of docker run commands:

#### Connecting to the docker engine from within the container
In order to get information from the docker enginer, we need to provide the hostname or IP address.  This can be done by adding the `-H=hostname` to the docker command such as `docker -H=172.17.0.1:2375 stats` or by setting the environment variable `DOCKER_HOST`.


##### Run a simple docker container using CCDP image
Creates a new container from the image and runs bash.  No CCDP is involved here. This is useful to modify the image 

`docker run -it --memory=512mb --rm -v /data/ccdp:/data/ccdp payero/centos-7:ccdp bash`

##### Run a simple docker container and runs the stress test 
Creates a new container from the image and runs CCDP CUP stress test.  

`docker run -it --rm -v /data/ccdp:/data/ccdp payero/centos-7:ccdp /data/ccdp/ccdp-engine/python/ccdp_mod_test.py -a testCpuUsage -p 10`


##### Run CCDP getting the installation package from S3
```
docker run -it --rm \
          --net=host \
          -e DOCKER_HOST=172.17.0.1:2375 \
          -e AWS_ACCESS_KEY_ID=<< YOUR KEY ID >> \
          -e AWS_SECRET_ACCESS_KEY=<< YOUR ACCESS KEY >> \
          -e AWS_DEFAULT_REGION=us-east-1 \          
          --entrypoint /data/ccdp/ccdp_install.py \
          payero/centos-7:ccdp \
          -a download \ 
          -d s3://ccdp-settings/ccdp-engine.tgz  \
          -t /data/ccdp/ -D
```
Running the container and defining the entrypoint as ccdp_install.py.  As mentioned before, the arguments to the entrypoint is passed after the image name.  In this case the container will call ccdp_install.py which gets the code from an S3 bucket, installs it locally, and starts the ccdp-agent.

The `--net=host` exposes all the ports from the host to the container.  I am sure there is a better way, I just need the time to find it.  The `-e` sets an environment variable to the user (root) running the command inside the container.

##### Run CCDP getting the installation package from filesystem

```
docker run -it --rm \
          --net=host \
          -e DOCKER_HOST=172.17.0.1:2375 \
          --entrypoint /data/ccdp/ccdp_install.py \
          payero/centos-7:ccdp \
          -t /data/ccdp \
          -D -n DOCKER
```
The same as the previous example except that it uses the file system to get the distribution code


### Getting container id from the container itself

Containers use `cgroup` which is a way to group resources by name.  Because of how containers get resources, we needed to create our own system resource monitoring mechanism which requires knowing the name of the container the resource monitor is running under.  In order to determine the name we used the following bash shell command:

`cat /proc/self/cgroup | grep "docker" | sed s/\\//\\n/g | tail -1`

The command above gets the long container id.  To get a shorter version matching what `docker ps` or `docker stats` displays we used the following command:

`cat /proc/self/cgroup | grep "docker" | sed s/\\//\\n/g | tail -1 | cut -c1-12`


### How to Modify an image and commit it/push it
1. create a container with an original image and start it

`docker run --name nginx-template-base -p 8080:80 -e TERM=xterm -d nginx`

**MAKE SURE YOU KNOW THE CONTAINER ID AS YOU WILL NEED IT**

2. access it and make changes
`docker exec -it CONTAINER_ID bash`

3. exit the container
4. commit the changes using the container ID
`docker commit CONTAINER_ID user/<base name>:<tag>`

4. push the changes to Docker.Hub
`docker push user/<base name>:<tag>`

### Docker API
The [Docker Containers API](https://docs.docker.com/engine/api/v1.21/#21-containers) gives you all the instructions to get information from the docker engine. For example to get all the containers information use the following query:

`curl http://172.17.0.1:2375/containers/json?all=1`

To get information for a specific container use the following URL:

`curl -i -H "Accept: application/json" -H "Content-Type: application/json" -X GET http://172.17.0.1:2375/containers/< CONTAINER ID >/json`

