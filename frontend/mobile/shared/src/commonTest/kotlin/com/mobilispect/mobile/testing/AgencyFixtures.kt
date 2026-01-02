package com.mobilispect.mobile.testing

const val AGENCIES_SUCCESSFUL_FIXTURE = """
                        {
                          "_embedded" : {
                            "agencies" : [ {
                              "uid" : "o-abcd-a",
                              "name" : "A"
                            }, {
                              "uid" : "o-abcd-b",
                              "name" : "B"
                            } ]
                          },
                          "_links" : {
                            "self" : {
                              "href" : "http://localhost:49336/agencies"
                            },
                            "profile" : {
                              "href" : "http://localhost:49336/profile/agencies"
                            }
                          },
                          "page" : {
                            "size" : 20,
                            "totalElements" : 2,
                            "totalPages" : 1,
                            "number" : 0
                          }
                        }
                    """
