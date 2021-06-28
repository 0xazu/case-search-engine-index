# case-search-engine-index

This repository contains my solution to the case search engine coding challenge. The objective of the system is to process documents from different sources and index them in solr.

## Caveats

* This is my first time using reactive programming and the AKKA framework. So it's probably I didn't follow all the best practices nor did everything the way it should be done using this architectural pattern.
* Due to the lack of time, test coverage is not at the level I would like to. I covered most of the general cases, but I'm missing corner cases and probably some behaviours.

## API


Here are the operations we expose:

### ```POST /documents```

It creates a new document under the documents resource.

#### Request body

* The ```name``` field is mandatory. The length must be < 50.
* The ```description``` field length must be < 200.
* The ```dataSource``` field is mandatory. It must be one of the following: PRODUCTS, PRICES or PROMOTIONS.
* The ```imagesUrls``` field has a limit of 10 elements.
* The ```promotion``` field length must be < 100.

An example of the request body:

```
{
    "name": "My beloved product",
    "description": "It's the best product in the market",
    "dataSource": "PRODUCTS"
}
```

And the response body:

```
{
    "id": "f65d620a-f10a-4549-b77b-d9f68f7ac029"
}
```

### ```PATCH /documents/{id}```

Updates the document referenced with the id passed in the request path. The same rules apply to the request body as in the POST operation except the name is not required here.
An example of the request body:

```
{
    "dataSource": "PRICES"
    "price": 12.95
}
```

### ```DELETE /documents/{id}```

It deletes the document referenced with the id passed in the request path.


## How to build & run the project

The project use gradle to build and run. The only external dependency is postgresql which can be run using docker-compose. The command to start postgresql is:

To build the project, run:

```docker-compose up --build postgres```

The application starts on localhost:8080
