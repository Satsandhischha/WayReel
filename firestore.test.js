const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validTripPayload(uid, created = new Date(Date.now() - 5000)) {
  return {
    userId: uid,
    title: "Tokyo Coffee & Views",
    daysCount: 3,
    startTimeMinutes: 540,
    reelSources: [
      {
        id: "reel_1",
        inputType: "caption",
        label: "Tokyo hidden gems",
        rawText: "Koffee Mameya in Omotesando...",
        reelUrl: "",
        status: "extracted",
      },
    ],
    places: [
      {
        id: "place_1",
        name: "Koffee Mameya",
        city: "Tokyo",
        country: "Japan",
        category: "cafe",
        whyMentioned: "Minimalist pour-over bar",
        confidence: "high",
        lat: 35.6655,
        lng: 139.7107,
        geocodeStatus: "resolved",
        displayAddress: "Omotesando, Shibuya, Tokyo",
        dayIndex: 0,
        orderInDay: 0,
        durationMinutes: 45,
        sourceReelId: "reel_1",
      },
    ],
    createdAt: created,
    updatedAt: created,
  };
}

test("Unauthenticated user cannot read or write trips or feedback", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("trips").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("trips").doc("trip_1").set(validTripPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("feedback").doc("fb_1").set({
      userId: ALICE_UID,
      tripId: "trip_1",
      rating: "yes",
      reelCount: 1,
      placeCount: 1,
      createdAt: new Date(Date.now() - 1000),
    })
  );
});

test("Authenticated owner can create, read, update, and delete valid trip", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const tripsCol = aliceDb.collection("users").doc(ALICE_UID).collection("trips");

  await assertSucceeds(tripsCol.doc("trip_1").set(validTripPayload(ALICE_UID, created)));
  await assertSucceeds(tripsCol.get());
  await assertSucceeds(tripsCol.doc("trip_1").get());

  // Partial update matching Kotlin TripRepository
  await assertSucceeds(
    tripsCol.doc("trip_1").update({
      title: "Updated Tokyo Trip",
      daysCount: 4,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Immutable createdAt cannot be modified
  await assertFails(
    tripsCol.doc("trip_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Extra @DocumentId 'id' field must be rejected
  await assertFails(
    tripsCol.doc("trip_2").set({
      id: "trip_2",
      ...validTripPayload(ALICE_UID, created),
    })
  );

  // >10 reels must be rejected
  const tooManyReels = Array.from({ length: 11 }, (_, i) => ({
    id: `reel_${i}`,
    inputType: "caption",
    label: "Reel",
    rawText: "text",
    reelUrl: "",
    status: "extracted",
  }));
  await assertFails(
    tripsCol.doc("trip_3").set({
      ...validTripPayload(ALICE_UID, created),
      reelSources: tooManyReels,
    })
  );

  await assertSucceeds(tripsCol.doc("trip_1").delete());
});

test("Cross-user isolation blocks reading or writing another user's trips and feedback", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(BOB_UID).collection("trips").doc("bob_trip").set(validTripPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("trips").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("trips").doc("bob_trip").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("trips").doc("bob_trip").update({
      title: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("trips").doc("bob_trip").delete()
  );
});

test("Authenticated owner can log valid feedback and invalid feedback is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const fbCol = aliceDb.collection("users").doc(ALICE_UID).collection("feedback");

  await assertSucceeds(
    fbCol.doc("fb_1").set({
      userId: ALICE_UID,
      tripId: "trip_1",
      rating: "mostly",
      comment: "Missed one bakery name spoken fast.",
      reelCount: 2,
      placeCount: 6,
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Without optional comment (omitted when null/blank)
  await assertSucceeds(
    fbCol.doc("fb_2").set({
      userId: ALICE_UID,
      tripId: "trip_1",
      rating: "yes",
      reelCount: 1,
      placeCount: 4,
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Invalid rating enum rejected
  await assertFails(
    fbCol.doc("fb_3").set({
      userId: ALICE_UID,
      tripId: "trip_1",
      rating: "amazing",
      reelCount: 1,
      placeCount: 4,
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Future timestamp rejected
  await assertFails(
    fbCol.doc("fb_4").set({
      userId: ALICE_UID,
      tripId: "trip_1",
      rating: "yes",
      reelCount: 1,
      placeCount: 4,
      createdAt: new Date(Date.now() + 60000),
    })
  );
});
