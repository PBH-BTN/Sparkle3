export interface SparkleBtnBasicInfo {
  id?: string | null;
  nickname?: string | null;
  registeredAt?: string | null;
  lastLoginAt?: string | null;
  banned?: boolean;
}

export interface SparkleBtnTraffic7d {
  enabled?: boolean;
  windowDays?: number;
  sentTrafficSelfReport?: string | null;
  receivedTrafficSelfReport?: string | null;
  sentTrafficOtherAck?: string | null;
  receivedTrafficOtherAck?: string | null;
  lastUpdatedAt?: string | null;
}

export interface SparkleBtnPoints {
  balanceBytes?: string | null;
}

export interface SparkleBtnTrafficRanking {
  rank?: number | null;
  sentTrafficSelfReport?: string | null;
  receivedTrafficSelfReport?: string | null;
  sentTrafficOtherAck?: string | null;
  receivedTrafficOtherAck?: string | null;
}

export interface SparkleBtnPointsRanking {
  rank?: number | null;
  balanceBytes?: string | null;
}

export interface SparkleBtnRankings {
  traffic?: SparkleBtnTrafficRanking | null;
  points?: SparkleBtnPointsRanking | null;
}

export interface SparkleBtnData {
  githubUid?: string | null;
  btnUserId: string;
  basicInfo?: SparkleBtnBasicInfo | null;
  traffic7d?: SparkleBtnTraffic7d | null;
  points?: SparkleBtnPoints | null;
  rankings?: SparkleBtnRankings | null;
  syncedAt?: string | null;
}
