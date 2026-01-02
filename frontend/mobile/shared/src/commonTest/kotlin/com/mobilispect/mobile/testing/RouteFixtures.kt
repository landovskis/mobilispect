package com.mobilispect.mobile.testing

const val ROUTES_SUCCESSFUL_FIXTURE = """
    {
     "_embedded" : {
    "routes" : [ {
      "uid" : "r-abcd-1",
      "shortName" : "1",
      "longName" : "Main Street",
      "agencyID" : "o-abcd-a"
    }, {
      "uid" : "r-abcd-2",
      "shortName" : "2",
      "longName" : "Central Avenue",
      "agencyID" : "o-abcd-a"
    } ]
  },
  "_links" : {
    "self" : {
      "href" : "http://localhost:64123/routes/search/findAllByAgencyID?id=o-abcd-a"
    }
  }
}
"""
