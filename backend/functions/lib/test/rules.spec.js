"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
// Firestore security rules tests. They need the Firestore emulator, so run them with
// `npm run test:rules`, which starts it for the duration of the tests.
const rules_unit_testing_1 = require("@firebase/rules-unit-testing");
const firestore_1 = require("firebase/firestore");
const node_fs_1 = require("node:fs");
const node_test_1 = require("node:test");
let env;
const save = {
    rev: 1, gems: 10, unlockedThemes: [], selectedTheme: "dusk", bests: { stack: 0, tower: 0 },
    streak: 0, lastDaily: -1, gamesPlayed: 0,
    settings: { sound: true, vibration: true, reduceMotion: false }, updatedAt: (0, firestore_1.serverTimestamp)(),
};
(0, node_test_1.before)(async () => {
    env = await (0, rules_unit_testing_1.initializeTestEnvironment)({
        projectId: "demo-stackdrop",
        firestore: { rules: (0, node_fs_1.readFileSync)("../firestore.rules", "utf8"), host: "127.0.0.1", port: 8080 },
    });
});
(0, node_test_1.beforeEach)(async () => {
    await env.clearFirestore();
});
(0, node_test_1.after)(async () => {
    await env.cleanup();
});
const as = (uid) => env.authenticatedContext(uid).firestore();
(0, node_test_1.test)("a player can create and read only their own save", async () => {
    await (0, rules_unit_testing_1.assertSucceeds)((0, firestore_1.setDoc)((0, firestore_1.doc)(as("alice"), "users/alice"), save));
    await (0, rules_unit_testing_1.assertSucceeds)((0, firestore_1.getDoc)((0, firestore_1.doc)(as("alice"), "users/alice")));
    await (0, rules_unit_testing_1.assertFails)((0, firestore_1.getDoc)((0, firestore_1.doc)(as("bob"), "users/alice")));
    await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)((0, firestore_1.doc)(as("bob"), "users/alice"), save));
});
(0, node_test_1.test)("the save rejects unknown fields and impossible gem counts", async () => {
    await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)((0, firestore_1.doc)(as("alice"), "users/alice"), { ...save, admin: true }));
    await (0, rules_unit_testing_1.assertSucceeds)((0, firestore_1.setDoc)((0, firestore_1.doc)(as("alice"), "users/alice"), save));
    await (0, rules_unit_testing_1.assertFails)((0, firestore_1.updateDoc)((0, firestore_1.doc)(as("alice"), "users/alice"), { gems: -1 }));
    await (0, rules_unit_testing_1.assertFails)((0, firestore_1.updateDoc)((0, firestore_1.doc)(as("alice"), "users/alice"), { gems: 2_000_000 }));
    await (0, rules_unit_testing_1.assertSucceeds)((0, firestore_1.updateDoc)((0, firestore_1.doc)(as("alice"), "users/alice"), { gems: 500, rev: 2 }));
});
(0, node_test_1.test)("profiles are public to signed-in players but written only by functions", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
        await (0, firestore_1.setDoc)((0, firestore_1.doc)(ctx.firestore(), "profiles/alice"), { name: "Alice", avatar: 1 });
    });
    await (0, rules_unit_testing_1.assertSucceeds)((0, firestore_1.getDoc)((0, firestore_1.doc)(as("bob"), "profiles/alice")));
    await (0, rules_unit_testing_1.assertFails)((0, firestore_1.getDoc)((0, firestore_1.doc)(env.unauthenticatedContext().firestore(), "profiles/alice")));
    await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)((0, firestore_1.doc)(as("alice"), "profiles/alice"), { name: "Hacker" }));
});
(0, node_test_1.test)("indexes, private data and leaderboard writes are closed to clients", async () => {
    await (0, rules_unit_testing_1.assertFails)((0, firestore_1.getDoc)((0, firestore_1.doc)(as("alice"), "usernames/alice")));
    await (0, rules_unit_testing_1.assertFails)((0, firestore_1.getDoc)((0, firestore_1.doc)(as("alice"), "friendCodes/K7Q2M9XD")));
    await (0, rules_unit_testing_1.assertFails)((0, firestore_1.getDoc)((0, firestore_1.doc)(as("alice"), "private/alice")));
    await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)((0, firestore_1.doc)(as("alice"), "leaderboards/tower_all/entries/alice"), { score: 9999 }));
    await (0, rules_unit_testing_1.assertSucceeds)((0, firestore_1.getDoc)((0, firestore_1.doc)(as("alice"), "leaderboards/tower_all/entries/bob")));
    await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)((0, firestore_1.doc)(as("alice"), "users/alice/friends/bob"), { since: 1 }));
});
//# sourceMappingURL=rules.spec.js.map