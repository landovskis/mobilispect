package com.mobilispect.mobile.testing

const val AGENCIES_SUCCESSFUL_FIXTURE = """
                        {
                          "_embedded" : {
                            "agencies" : [ {
                              "name" : "A",
                              "_links" : {
                                "self" : {
                                  "href" : "http://localhost:49336/agencies/o-abcd-a"
                                },
                                "agency" : {
                                  "href" : "http://localhost:49336/agencies/o-abcd-a"
                                }
                              }
                            }, {
                              "name" : "B",
                              "_links" : {
                                "self" : {
                                  "href" : "http://localhost:49336/agencies/o-abcd-b"
                                },
                                "agency" : {
                                  "href" : "http://localhost:49336/agencies/o-abcd-b"
                                }
                              }
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
