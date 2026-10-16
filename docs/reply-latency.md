# Reply latency, stock gateway — 2026-10-13

## Findings
Physical baseline: first text3.4s, final17.4s, first audio19.6s. Bounded sanitized history of the matching phone session confirms a roughly17.35-second story turn; previous short clarification roughly5.24s. No tools appear in these four bounded messages. History result was a bounded/truncated view, not a complete gateway audit. Session status: same configured model, medium reasoning, fast mode off;99% prompt cache hit in reported status. These observations do not separate all internal queue/model phases.

OpenAI's fetched latency guide emphasizes output generation as a major latency source and warns input-token reduction often offers little gain: https://developers.openai.com/api/docs/guides/latency-optimization . Search provider failed; direct official page fetch succeeded. No model switch, priority tier or prompt/context trimming was applied.

## Isolated comparison
Four fresh synthetic sessions using the same main agent/model, same harmless approximately150-word fictional-dog story request, no tools requested, deliver:false. Sequential order medium/low/low/medium. `chat.send` explicit thinking override followed by `agent.wait`:
- Medium:14.651s server run;16.32s CLI wall time.
- Low:14.980s server run;15.92s CLI wall time.
- Low:15.005s server run;15.95s CLI wall time.
- Medium:15.725s server run;16.72s CLI wall time.

Only two samples per setting, not a statistically significant quality/performance evaluation. No convincing benefit: reasoning/model/fast mode remain unchanged. Overrides were isolated to benchmark turns, not shared defaults or Maintainer's voice session.

## App change
Optional interim subscription moved to connection/agent-selection background setup. Send no longer waits for its acknowledgement. Pending subscription is reused; a failed optional attempt retries non-blockingly on a later send. Normal chat/final delivery remains independent. Connection/generation guards prevent stale acknowledgements changing a new subscription state.

A held-subscription regression proves chat.send proceeds while optional subscription is unresolved. This removes a potentially long avoidable stall (RPC timeout60s), but does NOT prove it caused the observed17.4-second generation delay or claim a measured phone speedup.

## Remaining limits
Same model + same complete150-word output can still take roughly15–17s. Further large reductions likely need a measured output-length/model/service tradeoff, not an unexplained app toggle. No such behavior/cost changes applied. Keep final-only speech; no forks.
