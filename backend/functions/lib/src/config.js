"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.MODES = exports.AVATAR_COUNT = exports.REGION = void 0;
const v2_1 = require("firebase-functions/v2");
/** Must match OnlineServices.REGION in the Android app and the Firestore location. */
exports.REGION = "us-central1";
/** Preset avatars drawn on the client; profiles store an index into them. */
exports.AVATAR_COUNT = 12;
exports.MODES = ["stack", "tower"];
(0, v2_1.setGlobalOptions)({ region: exports.REGION, maxInstances: 10 });
//# sourceMappingURL=config.js.map