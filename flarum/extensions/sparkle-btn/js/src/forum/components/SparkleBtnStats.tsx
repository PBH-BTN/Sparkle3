import app from "flarum/forum/app";
import Component, { type ComponentAttrs } from "flarum/common/Component";
import type User from "flarum/common/models/User";
import type { SparkleBtnData, SparkleBtnTraffic7d } from "../../common/types";

interface IAttrs extends ComponentAttrs {
  user: User;
}

type UserWithSparkle = User & {
  sparkleBtn?: () => SparkleBtnData | null;
};

const TRAFFIC_KEYS = [
  "sentTrafficSelfReport",
  "receivedTrafficSelfReport",
  "sentTrafficOtherAck",
  "receivedTrafficOtherAck",
] as const;

export default class SparkleBtnStats extends Component<IAttrs> {
  view() {
    const user = this.attrs.user as UserWithSparkle;
    const data = user.sparkleBtn?.();

    if (!data?.btnUserId) {
      return null;
    }

    return (
      <section className="SparkleBtnStats">
        <header className="SparkleBtnStats-header">
          <span className="SparkleBtnStats-brand">
            <i className="fas fa-satellite-dish" aria-hidden="true" />
            {app.translator.trans("sparkle-btn-flarum.forum.card.title")}
          </span>
          <span className="SparkleBtnStats-uid">
            {app.translator.trans("sparkle-btn-flarum.forum.card.uid", {
              uid: data.btnUserId,
            })}
          </span>
        </header>

        {this.onlyVisibleToSelf(user) && (
          <div className="SparkleBtnStats-private">
            {app.translator.trans("sparkle-btn-flarum.forum.card.only_self")}
          </div>
        )}

        {this.identity(data)}
        {this.traffic(data)}

        <div className="SparkleBtnStats-summary">
          <div className="SparkleBtnStats-summaryItem">
            <span>
              {app.translator.trans("sparkle-btn-flarum.forum.card.points")}
            </span>
            <strong
              title={
                this.pointsVisible(data)
                  ? this.rawBytes(data.points?.balanceBytes)
                  : undefined
              }
            >
              {this.pointsVisible(data)
                ? this.formatBytes(data.points?.balanceBytes)
                : this.hiddenLabel(data)}
            </strong>
          </div>
          <div className="SparkleBtnStats-summaryItem">
            <span>
              {app.translator.trans(
                "sparkle-btn-flarum.forum.card.traffic_rank",
              )}
            </span>
            <strong>{this.formatRank(data.rankings?.traffic?.rank)}</strong>
          </div>
          <div className="SparkleBtnStats-summaryItem">
            <span>
              {app.translator.trans(
                "sparkle-btn-flarum.forum.card.points_rank",
              )}
            </span>
            <strong>
              {this.pointsVisible(data)
                ? this.formatRank(data.rankings?.points?.rank)
                : this.hiddenLabel(data)}
            </strong>
          </div>
        </div>

        {data.syncedAt && (
          <time className="SparkleBtnStats-updated" datetime={data.syncedAt}>
            {app.translator.trans("sparkle-btn-flarum.forum.card.updated", {
              time: this.formatDate(data.syncedAt),
            })}
          </time>
        )}
      </section>
    );
  }

  private identity(data: SparkleBtnData) {
    const info = data.basicInfo;
    if (!info?.nickname && !info?.registeredAt && !info?.banned) {
      return null;
    }

    return (
      <div className="SparkleBtnStats-identity">
        {info?.nickname && <strong>{info.nickname}</strong>}
        {info?.registeredAt && (
          <span>
            {app.translator.trans("sparkle-btn-flarum.forum.card.registered", {
              date: this.formatDate(info.registeredAt),
            })}
          </span>
        )}
        {info?.banned && (
          <span className="SparkleBtnStats-banned">
            {app.translator.trans("sparkle-btn-flarum.forum.card.banned")}
          </span>
        )}
      </div>
    );
  }

  private traffic(data: SparkleBtnData) {
    const traffic = data.traffic7d;
    if (!traffic?.enabled) {
      return (
        <div className="SparkleBtnStats-empty">
          {app.translator.trans(
            "sparkle-btn-flarum.forum.card.traffic_disabled",
          )}
        </div>
      );
    }

    const maximum = TRAFFIC_KEYS.reduce((max, key) => {
      const value = this.toBigInt(traffic[key]);
      return value > max ? value : max;
    }, 0n);

    return (
      <div className="SparkleBtnStats-traffic">
        <div className="SparkleBtnStats-sectionHeading">
          <span>
            {app.translator.trans("sparkle-btn-flarum.forum.card.traffic", {
              days: traffic.windowDays ?? 7,
            })}
          </span>
          {maximum === 0n && (
            <small>
              {app.translator.trans("sparkle-btn-flarum.forum.card.no_traffic")}
            </small>
          )}
        </div>

        <div className="SparkleBtnStats-trafficGrid">
          {TRAFFIC_KEYS.map((key) => this.trafficRow(traffic, key, maximum))}
        </div>
      </div>
    );
  }

  private trafficRow(
    traffic: SparkleBtnTraffic7d,
    key: (typeof TRAFFIC_KEYS)[number],
    maximum: bigint,
  ) {
    const value = this.toBigInt(traffic[key]);
    const width = maximum === 0n ? 0 : Number((value * 1000n) / maximum) / 10;
    const labelKey = {
      sentTrafficSelfReport: "sent_self",
      receivedTrafficSelfReport: "received_self",
      sentTrafficOtherAck: "sent_ack",
      receivedTrafficOtherAck: "received_ack",
    }[key];

    return (
      <div className="SparkleBtnStats-trafficRow" key={key}>
        <span className="SparkleBtnStats-trafficLabel">
          {app.translator.trans(`sparkle-btn-flarum.forum.card.${labelKey}`)}
        </span>
        <span className="SparkleBtnStats-bar" aria-hidden="true">
          <span
            className={`SparkleBtnStats-barFill SparkleBtnStats-barFill--${key}`}
            style={{ width: `${width}%` }}
          />
        </span>
        <strong title={this.rawBytes(value)}>{this.formatBytes(value)}</strong>
      </div>
    );
  }

  private onlyVisibleToSelf(user: UserWithSparkle): boolean {
    return (
      app.session.user?.id() === user.id() &&
      user.preferences()?.sparkleBtnShowCard === false
    );
  }

  private pointsVisible(data: SparkleBtnData): boolean {
    return data.privacy?.pointsVisible !== false && data.points != null;
  }

  private hiddenLabel(data: SparkleBtnData) {
    return data.privacy?.pointsVisible === false
      ? app.translator.trans("sparkle-btn-flarum.forum.card.hidden")
      : "—";
  }

  private toBigInt(value: unknown): bigint {
    try {
      return BigInt(String(value ?? 0));
    } catch {
      return 0n;
    }
  }

  private rawBytes(value: unknown): string {
    return `${this.toBigInt(value)} Bytes`;
  }

  private formatBytes(value: unknown): string {
    let amount = this.toBigInt(value);
    const negative = amount < 0n;
    if (negative) amount = -amount;

    const units = ["B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB"];
    let unit = 0;
    while (amount >= 1024n && unit < units.length - 1) {
      amount /= 1024n;
      unit++;
    }

    const numeric = Number(amount);
    const decimals = numeric >= 100 ? 0 : numeric >= 10 ? 1 : 2;
    const formatted = `${negative ? "-" : ""}${numeric.toFixed(decimals).replace(/\.0+$|(\.\d*[1-9])0+$/, "$1")}`;

    return `${formatted} ${units[unit]}`;
  }

  private formatRank(rank: number | null | undefined) {
    return rank == null
      ? app.translator.trans("sparkle-btn-flarum.forum.card.unranked")
      : `#${rank.toLocaleString()}`;
  }

  private formatDate(value: string): string {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;

    return new Intl.DateTimeFormat(undefined, {
      year: "numeric",
      month: "short",
      day: "numeric",
    }).format(date);
  }
}
