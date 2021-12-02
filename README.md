DOJO, the Database of JSON Objects
===================================

This is a web server that receives HTTP requests with (mostly) arbitrary JSON data and stores them in a database for later retrieval. It's intended to be used as a logging endpoint for development/testing and in simple applications.

# What problem does this solve?
You have an app and want to send data (e.g., events, logs, etc.) to a database. You don't want to integrate any database connectors or other libraries into the app; you just want to make an HTTPS request and forget about it.

# Prerequisites

You'll need a Java Runtime Environment (JRE) installed. For example, on Debian/Ubuntu, you can run `sudo apt install default-jre`.  
The current build targets version 14 or newer of the JDK.

# Run

    java -jar <path-to-jar>.jar

The server will launch and start listening. See below for how to interact with it.

## Credentials

The server allows retrieval of uploaded data; this is secured using HTTP basic authentication. To specify the credentials, set the environment variable `ADMIN_CREDENTIALS` using the pattern `username:password`.

## Database configuration

By default, data sent to the server will be stored in a `db.sqlite` file in your current directory. To reset the server, delete that file.

To specify an alternate location, set the environment variable `DATABASE_URL` to `jdbc:sqlite:/path/to/db.sqlite`.

The application code is [database-agnostic](https://github.com/JetBrains/Exposed#supported-databases) and bundles a PostgreSQL driver, so providing a PostgreSQL connection string should also work.
However, this behavior has not been tested.

## Long-running process

This repository includes several additional files that help launch the server in a long-running process:

- `start-server.sh` demonstrates loading environment variables (which may include secrets, for example) and recommended JVM flags
- `server.service` is a systemd service file that shows how to daemonize the launch script, so that it restarts automatically
- `Dockerfile` shows how the application can be made into a Docker container

# API

This section describes the behavior of the API endpoints exposed by the server.

## `GET /v1/status`

Check that the server is up and running

- run SQL query: SELECT 1;
- if it returns, print "OK" else print the error

## `POST /v1/objects`

Add new data to the server

1. receive JSON object
2. validate that it that has the following fields:
    - api_version (number) MUST be 1
    - name (string)
      - the name/type of the event being stored
    - value (JSON)
      - data to store
    - client_version (string)
       - version or other identifier for the client sending the data
    - user_id (string)
       - an identifier for the user this data is coming from
    - user_label (OPTIONAL, string)
       - an optional identifier for the user this data is coming from
3. randomly generate a 128-bit value. this will be the update_token
4. create a new row in the database with the provided values, plus the update_token
   - fill in the created_at and updated_at with the current timestamp
   - get the id of the new row
5. return the response  
   `{id: id, token: update_token}`

## `POST /v1/objects/<id>`

Update a piece of data on the server

1. receive JSON object
2. validate that it has the following fields:
    - api_version (number) MUST be 1
    - client_version (string)
      - version or other identifier for the client sending the data
    - id (string)
      - the id of the object to be updated (returned during creation)
    - token (string)
       - the update_token of the object to be updated (returned during creation)
    - value (JSON)
       - the new value
    - revision (number)
      - a number indicating the current revision of the object.  
        Must be greater than 0 and increase with every update.  
        Updates with a revision value less than the currently stored one will be rejected.
3. run SQL query: SELECT token, revision FROM objects WHERE id = id;
4. if that returns no results, return `{"error": "invalid id"}`
5. validate token. if invalid, return `{"error": "invalid token"}`
6. validate revision: submitted revision must be > saved revision. if it isn't, return `{"error": "revision", "submitted_revision": submitted_revision, "latest_revision": revision}`
7. run SQL query updating the row in the database
    - update client_version if applicable
    - update updated_at

## `GET /v1/objects/all`

Retrieve all data from the server

 1. use basic auth to validate credentials (default username: testing ; password: changeit)
 2. check if it has the following field:
     - start (OPTIONAL, must be int)
 3. run SQL query: SELECT * FROM objects WHERE id > start LIMIT 51
 4. if the size of the resultset == 51, remove the last row and save its ID as next_id
 5. return `{objects: [list of objects], next: next_id or null}`

# See also

This project is the successor to a project with a similar goal [built on top of a serverless Firebase infrastructure](https://github.com/nmalkin/siskel).
In general, there's a lot of other software that can enable easy logging by providing a simple HTTP frontend to a database.
Some choices to consider include [CouchDB](https://couchdb.apache.org/) and [PostgREST](https://postgrest.org/).
