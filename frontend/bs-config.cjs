module.exports = {
  proxy: "localhost:9000",
  files: [
    "../server/src/main/public/**/*",
    "../server/src/main/twirl/views/**/*.scala.html",
  ],
  open: false,
  notify: false,
};
