// Firestore 규칙 테스트 (NFR-04, FR-01) — 에뮬레이터에서 실행: npm run test:rules
// 연결된 보호자 / 연결 안 된 사용자 / 연결 해제된 사용자 각각의 접근을 확인한다.
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { after, before, beforeEach, describe, it } from "mocha";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import {
  Timestamp,
  collection,
  deleteField,
  doc,
  getDoc,
  getDocs,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
} from "firebase/firestore";

const here = dirname(fileURLToPath(import.meta.url));
let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-gpsplace",
    firestore: { rules: readFileSync(join(here, "..", "..", "firestore.rules"), "utf8"), host: "127.0.0.1", port: 8080 },
  });
});

after(async () => env.cleanup());
beforeEach(async () => env.clearFirestore());

const G = "guardian-uid";
const P = "protected-uid";
const STRANGER = "stranger-uid";

const db = (uid) => env.authenticatedContext(uid).firestore();

async function seed(fn) {
  await env.withSecurityRulesDisabled(async (ctx) => fn(ctx.firestore()));
}

async function seedActiveLink(status = "active") {
  await seed(async (s) => {
    const link = { guardianUid: G, protectedUid: P, status, codeId: "123456", createdAt: Timestamp.now(), pendingCount: 0 };
    if (status === "active") link.lastPoint = { lat: 37.5, lng: 127.0, t: 1 };
    await setDoc(doc(s, "links/L1"), link);
    await setDoc(doc(s, "links/L1/pointHours/2026-10-30_09"), { dayKey: "2026-10-30", hour: 9, points: {} });
    await setDoc(doc(s, "links/L1/stays/s1"), { startAt: 1, endAt: 2, lat: 1, lng: 1, dayKey: "2026-10-30", durationSec: 0 });
    await setDoc(doc(s, "links/L1/zones/z1"), { name: "집", type: "safe", lat: 1, lng: 1, radiusM: 200 });
  });
}

describe("NFR-04 위치 정보 접근 제어", () => {
  it("연결된 보호자는 최신 위치·하루 경로를 읽는다", async () => {
    await seedActiveLink();
    await assertSucceeds(getDoc(doc(db(G), "links/L1")));
    await assertSucceeds(getDocs(query(collection(db(G), "links/L1/pointHours"), where("dayKey", "==", "2026-10-30"))));
    await assertSucceeds(getDocs(query(collection(db(G), "links/L1/stays"), where("dayKey", "==", "2026-10-30"))));
  });

  it("연결 안 된 사용자는 연결·위치·체류를 읽을 수 없다", async () => {
    await seedActiveLink();
    await assertFails(getDoc(doc(db(STRANGER), "links/L1")));
    await assertFails(getDocs(collection(db(STRANGER), "links/L1/pointHours")));
    await assertFails(getDocs(collection(db(STRANGER), "links/L1/stays")));
    await assertFails(getDocs(collection(env.unauthenticatedContext().firestore(), "links/L1/pointHours")));
  });

  it("연결 해제 후에는 보호자·보호대상자 모두 위치·기록을 읽을 수 없다", async () => {
    await seedActiveLink("released");
    for (const uid of [G, P]) {
      await assertFails(getDocs(collection(db(uid), "links/L1/pointHours")));
      await assertFails(getDocs(collection(db(uid), "links/L1/stays")));
      await assertFails(getDocs(collection(db(uid), "links/L1/zones")));
    }
    // 위치가 지워진 해제 문서 자체는 당사자가 볼 수 있다 (해제 사실 확인용)
    await assertSucceeds(getDoc(doc(db(G), "links/L1")));
  });

  it("위치는 보호대상자만 쓰고, 시간 문서 키와 dayKey·hour가 맞아야 한다", async () => {
    await seedActiveLink();
    const hour = { dayKey: "2026-10-30", hour: 10, startAt: 0, points: { p1: { lat: 1, lng: 1, t: 1 } } };
    await assertSucceeds(setDoc(doc(db(P), "links/L1/pointHours/2026-10-30_10"), hour));
    await assertFails(setDoc(doc(db(G), "links/L1/pointHours/2026-10-30_10"), hour));
    await assertFails(setDoc(doc(db(P), "links/L1/pointHours/2026-10-30_11"), hour));
    await assertFails(setDoc(doc(db(P), "links/L1/pointHours/2026-10-31_10"), hour));
  });

  it("구역은 보호자만 쓰고, 이름 1~10자·반경 50m~2km만 저장된다 (FR-04)", async () => {
    await seedActiveLink();
    const zone = { name: "학교", type: "caution", lat: 1, lng: 1, radiusM: 300 };
    await assertSucceeds(setDoc(doc(db(G), "links/L1/zones/z2"), zone));
    await assertFails(setDoc(doc(db(P), "links/L1/zones/z3"), zone));
    await assertFails(setDoc(doc(db(G), "links/L1/zones/z4"), { ...zone, name: "" }));
    await assertFails(setDoc(doc(db(G), "links/L1/zones/z5"), { ...zone, name: "열한글자이름입니다요" + "!" }));
    await assertFails(setDoc(doc(db(G), "links/L1/zones/z6"), { ...zone, radiusM: 40 }));
    await assertFails(setDoc(doc(db(G), "links/L1/zones/z7"), { ...zone, radiusM: 2500 }));
  });

  it("해제는 lastPoint를 지울 때만 허용되고, 해제 뒤에는 보고를 쓸 수 없다", async () => {
    await seedActiveLink();
    await assertFails(updateDoc(doc(db(G), "links/L1"), { status: "released", releasedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(db(G), "links/L1"), { status: "released", releasedAt: serverTimestamp(), lastPoint: deleteField() }));
    await assertFails(updateDoc(doc(db(P), "links/L1"), { pendingCount: 3 }));
  });

  it("보호자는 보호대상자 상태 보고(lastPoint·pendingCount)를 쓸 수 없다", async () => {
    await seedActiveLink();
    await assertSucceeds(updateDoc(doc(db(P), "links/L1"), { pendingCount: 2, reportedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db(G), "links/L1"), { pendingCount: 0 }));
  });
});

describe("FR-01 기기 연결", () => {
  it("코드 발급 → 보호대상자 확인 → 동의로 연결이 만들어진다", async () => {
    await assertSucceeds(
      setDoc(doc(db(G), "codes/482913"), {
        guardianUid: G,
        status: "waiting",
        createdAt: serverTimestamp(),
        expiresAt: Timestamp.fromMillis(Date.now() + 600000),
      }),
    );
    await assertSucceeds(updateDoc(doc(db(P), "codes/482913"), { status: "pending_consent", protectedUid: P }));

    const p = db(P);
    const batch = writeBatch(p);
    batch.set(doc(p, "links/NEW"), {
      guardianUid: G,
      protectedUid: P,
      status: "active",
      codeId: "482913",
      createdAt: serverTimestamp(),
      pendingCount: 0,
      permission: { location: "granted", service: "on" },
      reportedAt: serverTimestamp(),
    });
    batch.update(doc(p, "codes/482913"), { status: "consumed", linkId: "NEW" });
    batch.set(doc(p, "users/" + P), { role: "protected", linkId: "NEW" }, { merge: true });
    await assertSucceeds(batch.commit());
    await assertSucceeds(getDoc(doc(db(G), "links/NEW")));
  });

  it("동의하지 않은 코드로는 연결을 만들 수 없다 (다른 사용자 코드 가로채기 차단)", async () => {
    await seed(async (s) =>
      setDoc(doc(s, "codes/111111"), { guardianUid: G, status: "waiting", createdAt: Timestamp.now(), expiresAt: Timestamp.now() }),
    );
    const x = db(STRANGER);
    const batch = writeBatch(x);
    batch.set(doc(x, "links/BAD"), {
      guardianUid: G, protectedUid: STRANGER, status: "active", codeId: "111111",
      createdAt: serverTimestamp(), pendingCount: 0, permission: {}, reportedAt: serverTimestamp(),
    });
    batch.update(doc(x, "codes/111111"), { status: "consumed", linkId: "BAD" });
    await assertFails(batch.commit());
  });

  it("10분이 지난 코드는 확인할 수 없다 (UC-01 A1)", async () => {
    await seed(async (s) =>
      setDoc(doc(s, "codes/222222"), {
        guardianUid: G,
        status: "waiting",
        createdAt: Timestamp.fromMillis(Date.now() - 11 * 60 * 1000),
        expiresAt: Timestamp.fromMillis(Date.now() - 60 * 1000),
      }),
    );
    await assertFails(updateDoc(doc(db(P), "codes/222222"), { status: "pending_consent", protectedUid: P }));
  });

  it("코드는 목록으로 조회할 수 없고, 6자리 숫자만 만들 수 있다", async () => {
    await assertFails(getDocs(collection(db(STRANGER), "codes")));
    await assertFails(
      setDoc(doc(db(G), "codes/12ab"), { guardianUid: G, status: "waiting", createdAt: serverTimestamp(), expiresAt: Timestamp.now() }),
    );
  });

  it("보호대상자는 거부할 수 있고, 보호자는 대기 중인 코드를 취소할 수 있다", async () => {
    await seed(async (s) => {
      await setDoc(doc(s, "codes/333333"), { guardianUid: G, status: "pending_consent", protectedUid: P, createdAt: Timestamp.now(), expiresAt: Timestamp.now() });
      await setDoc(doc(s, "codes/444444"), { guardianUid: G, status: "waiting", createdAt: Timestamp.now(), expiresAt: Timestamp.now() });
    });
    await assertSucceeds(updateDoc(doc(db(P), "codes/333333"), { status: "denied" }));
    await assertSucceeds(updateDoc(doc(db(G), "codes/444444"), { status: "cancelled" }));
    await assertFails(updateDoc(doc(db(STRANGER), "codes/444444"), { status: "cancelled" }));
  });
});
