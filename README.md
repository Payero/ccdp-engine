# Cloud Computing Data Processing (CCDP)

## Web Application

  There are two directories; frontend and services.  The frontend is the web
application itelf.  The services contains a single docker-compose.yml file with
docker containers shared among multiple projects such as ActiveMQ and MongoDb.

  Before we run the GUI we need to make sure both containers are running:

  ```
  docker ps
  ```
  You should see something similar to this:
  
```
CONTAINER ID        IMAGE             COMMAND                  STATUS   NAMES
8f57fac375bd        activemq:5.14.3   "/app/run.sh"            Up       amq
5285307c71f9        mongo:3.2         "docker-entrypoint..."   Up       mongo
```

  If you want to see a working version do the following (thanks Kevin Kelly):
- Clone the gui-angular project
- Get the feat-react-temp
- docker-compose build && docker-compose up
- http://&lt;hostname&gt;:20223 shows the angular version that we n longer use
- http://&lt;hostname&gt;:20223/react shows the React version we are incorporating

### To Do:
- Is not running, there is an error message about port 61613, don't know why. 
    - It works if you run the same python code outside the container
    - It might be something to do with how the frontend container is launched
- Find a better way to deply the whole thing:
    - Can we check if the docker containers are running?
- This file is not truly what needs to be executed in order to run the GUI so we need to finish it
- Add a way to upload modules as zip files into a S3 bucket





### Installing

- Ensure you have [Docker]() and [Docker-Compose]() installed.

### Running

- First, build the docker images. Thankfully, `docker-compose` provides a way to orchestrate multiple Docker containers.

    ```
    docker-compose build
    ```

- Then, run the docker images.

    ```
    docker-compose up
    ```

- At this point, mongo may be empty, so we provide `modules-mongo.json` for seeding. To seed the database, copy `modules-mongo.json` to `ccdp/webapp/data/`. Then, enter the docker container running mongo and import the data. Below illustrates each step.

    ```
    ~/ccdp $ mkdir -p webapp/data # you may need to make a data/ subdir under webapp/
    ~/ccdp $ cp modules-mongo.json webapp/data/. # copy json seed file to data dir (data dir is mounted as volume to mongo docker container)
    ~/ccdp $ docker ps # view the currently running docker containers
    CONTAINER ID        IMAGE               COMMAND                  CREATED             STATUS              PORTS                     NAMES
    9d80e64e2621        webapp_app          "python server.py --i"   11 minutes ago      Up 10 minutes       0.0.0.0:20223->5000/tcp   webapp_app_1
    8ca20944891b        mongo:3.2           "/entrypoint.sh mongo"   28 minutes ago      Up 10 minutes       27017/tcp                 webapp_db_1
    ~/ccdp $ docker exec -it 8ca20944891b bash -l # enter the container with ID=8ca20944891b and execute `bash -l` (giving you an interactive shell)
    root@mongo:/# mongoimport --db ccdp --collection modules --jsonArray /data/db/modules-mongo.json
    2016-07-12T18:28:41.963+0000	connected to: localhost
    2016-07-12T18:28:41.969+0000	imported 3 documents
    ```

- Now, by navigating to localhost:20223 (if you are running this on another server, try running <IP_OF_SERVER>:20223), you should be able to see the webapp with several modules listed.

## Engine

### Installing

### Running