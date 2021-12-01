DOJO, the Database of JSON Objects
===================================

# Prerequisites

You'll need a Java Runtime Environment (JRE) installed. For example, on Debian/Ubuntu, you can run `sudo apt install default-jre`.  
Currently, version 14 or newer of the JDK is supported.

# Run

    java -jar <path-to-jar>.jar

The server will launch and start listening. See the next section for how to interact with it.

Note that this will create a `db.sqlite` file in your current directory. To reset the server, delete that file.

# Endpoints

## `GET /v1/status`

- run SQL query: SELECT 1;
- if it returns, print "OK" else print the error

## `POST /v1/objects`

1. receive JSON object
2. validate that it that has the following fields:
    - api_version MUST be 1
    - name
    - value
    - client_version
    - user_id
    - user_label (OPTIONAL)
3. randomly generate a 128-bit value. this will be the update_token
4. create a new row in the database with the provided values, plus the update_token
   - fill in the created_at and updated_at with the current timestamp
   - get the id of the new row
5. return the response {id: id, token: update_token} (filling in the id and update_token accordingly)

## `POST /v1/objects/<id>`

1. receive JSON object
2. validate that it has the following fields:
    - api_version MUST be 1
    - client_version
    - id
    - token
    - value
    - revision
3. run SQL query: SELECT token, revision FROM objects WHERE id = id;
4. if that returns no results, return {"error": "invalid id"}
5. validate token. if invalid, return {"error": "invalid token"}
6. validate revision: submitted revision must be > saved revision. if it isn't, return {"error": "revision", "submitted_revision": submitted_revision, "latest_revision": revision}
6. run SQL query updating the row in the database
    - update client_version if applicable
    - update updated_at

## `GET /v1/objects/all`

 1. use basic auth to validate credentials (default username: testing ; password: changeit)
 2. check if it has the following field:
     - start (OPTIONAL, must be int)
 3. run SQL query: SELECT * FROM objects WHERE id > start LIMIT 51
 4. if the size of the resultset == 51, remove the last row and save its ID as next_id
 5. return {objects: [list of objects], next: next_id or null}


# SQL table structure

This is approximate; see code for actual implementation.

```sql
CREATE TABLE objects
id int autoincrement NOT NULL
created_at int NOT NULL
updated_at int NOT NULL
name varchar32 NOT NULL
value text NOT NULL
update_token varchar64 NOT NULL
client_version_first varchar16 NOT NULL
client_version_last varchar16 NOT NULL
user_id varchar32 NOT NULL
user_label varchar32
revision int NOT NULL
```