self.__MIDDLEWARE_MATCHERS = [
  {
    "regexp": "^\\/bb(?:\\/(_next\\/data\\/[^/]{1,}))?\\/bb(?:\\/((?:[^\\/#\\?]+?)(?:\\/(?:[^\\/#\\?]+?))*))?(\\.json)?[\\/#\\?]?$",
    "originalSource": "/bb/:path*"
  }
];self.__MIDDLEWARE_MATCHERS_CB && self.__MIDDLEWARE_MATCHERS_CB()