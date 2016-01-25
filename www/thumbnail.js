/*global cordova, module*/

module.exports = {
    makeThumbnail: function (successCallback, errorCallback, uri, maxWidth, maxHeight, quality) {
        cordova.exec(successCallback, errorCallback, "Thumbnail", "makeThumbnail", [uri, maxWidth, maxHeight, quality]);
    }
};