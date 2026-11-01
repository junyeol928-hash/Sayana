# デザインの相互レビュー記録

Claude と Codex が交互にレビューし、Awwwards の 4 項目(Design / Usability / Creativity / Content、各 10 点)で
**両方の採点が全項目 8.5 以上**になるまで直して回す。設計の意図は [design.md](design.md)。

- レビューの頼み方: 撮影(`docs/screenshots/roundN/`)と、ソース・`docs/design.md` を読んでもらう。同じ文面の依頼を両者に出す
- Claude 側は、作業している Claude とは別の Claude(サブエージェント。作業の経緯を知らない状態)が採点する
- Codex 側は `codex exec -s read-only` で、撮影を添付して採点する
- 撮影した端末は「視差効果を減らす」がオンのため、動きは止まった状態で写っている(採点者には CSS を読んで判断してもらった)

## 点数の推移

| 回 | 採点者 | Design | Usability | Creativity | Content | 全項目 8.5 以上 |
|---|---|---|---|---|---|---|
