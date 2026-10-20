"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.removeFriend = exports.respondFriendRequest = exports.sendFriendRequest = exports.submitScore = exports.deleteAccount = exports.setAvatar = exports.claimName = void 0;
require("./config");
var profile_1 = require("./profile");
Object.defineProperty(exports, "claimName", { enumerable: true, get: function () { return profile_1.claimName; } });
Object.defineProperty(exports, "setAvatar", { enumerable: true, get: function () { return profile_1.setAvatar; } });
var account_1 = require("./account");
Object.defineProperty(exports, "deleteAccount", { enumerable: true, get: function () { return account_1.deleteAccount; } });
var scores_1 = require("./scores");
Object.defineProperty(exports, "submitScore", { enumerable: true, get: function () { return scores_1.submitScore; } });
var friends_1 = require("./friends");
Object.defineProperty(exports, "sendFriendRequest", { enumerable: true, get: function () { return friends_1.sendFriendRequest; } });
Object.defineProperty(exports, "respondFriendRequest", { enumerable: true, get: function () { return friends_1.respondFriendRequest; } });
Object.defineProperty(exports, "removeFriend", { enumerable: true, get: function () { return friends_1.removeFriend; } });
//# sourceMappingURL=index.js.map