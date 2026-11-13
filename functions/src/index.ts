// 구역 알림 푸시 (FR-04, NFR-05)
// 보호대상자 기기가 links/{linkId}/alerts/{alertId}를 만들면 연결된 보호자 기기로 FCM을 보낸다.
// 알림만 보낸다 — 전화·문자·외부 기관 신고는 하지 않는다 (FR-09 Won't).
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { setGlobalOptions } from "firebase-functions/v2";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import * as logger from "firebase-functions/logger";

initializeApp();

// Firestore 데이터베이스 “gpsplace”가 서울 리전에 있으므로 트리거도 같은 리전에 둔다.
// maxInstances로 동시에 뜨는 인스턴스 수를 묶어 비용을 제한한다.
setGlobalOptions({ region: "asia-northeast3", maxInstances: 10 });

const DATABASE_ID = "gpsplace";

export const onAlertCreated = onDocumentCreated(
  { document: "links/{linkId}/alerts/{alertId}", database: DATABASE_ID },
  async (event) => {
    const alert = event.data?.data();
    if (!alert) return;

    const db = getFirestore(DATABASE_ID);
    const link = await db.doc(`links/${event.params.linkId}`).get();
    if (!link.exists || link.get("status") !== "active") {
      logger.info("연결이 없거나 해제됨 — 알림을 보내지 않음", { linkId: event.params.linkId });
      return;
    }

    const guardian = await db.doc(`users/${link.get("guardianUid")}`).get();
    const token = guardian.get("fcmToken");
    if (typeof token !== "string" || token.length === 0) {
      logger.info("보호자 기기 토큰 없음", { linkId: event.params.linkId });
      return;
    }

    // 데이터 메시지: 앱이 꺼져 있어도 FcmService가 받아 알림을 만들고, 누르면 S-09로 연다.
    try {
      await getMessaging().send({
        token,
        data: {
          alertId: event.params.alertId,
          type: String(alert.type ?? ""),
          zoneName: String(alert.zoneName ?? ""),
          t: String(alert.p2?.t ?? ""),
        },
        android: { priority: "high" },
      });
    } catch (error) {
      logger.warn("FCM 전송 실패", { linkId: event.params.linkId, error: String(error) });
    }
  },
);
