(ns steemobservatory.client
  (:require [reagent.core :as r]
            [cljs.pprint :as pprint]))

(enable-console-print!)

(defonce articles (r/atom []))
(defonce avatar (r/atom ""))
(defonce account (r/atom {}))

(defn parseAvatarUrl [account]
  (let [parsed (js/JSON.parse (get account "json_metadata"))
        meta (js->clj parsed)]
    (get-in meta ["profile" "profile_image"])))

(defn getDiscussions []
  (.then
    (js/steem.database.getDiscussions
      "blog"
      (clj->js {:limit 10
                :tag "crypticwyrm"}))
    (fn [result]
      (swap! articles
        (fn []
          (map js->clj result))))
    (fn [e] (js/console.log e))))

(defn getAccounts []
  (.then
    (js/steem.database.getAccounts (clj->js ["crypticwyrm"]))
    (fn [result]
      (js/console.log (first result))
      (reset! account (js->clj (first result)))
      (reset! avatar
        (parseAvatarUrl
          (js->clj (first result)))))
    (fn [e] (js/console.log e))))

(defn is-article-active [article]
  (let [cashout (js/Date. (get article "cashout_time"))
        now (js/Date.)]
    (> (- cashout now) 0)))

(defn voting-power-to-percent [vp]
  (js/Math.round (/ vp 100)))

(defn article-item [article]
  (let [cashout (js/Date. (get article "cashout_time"))
        now (js/Date.)
        active (> (- cashout now) 0)
        worth (if active
                (get article "pending_payout_value")
                (get article "total_payout_value"))]
    [:div {:class "article"
           :on-click (fn []
                       (js/console.log (clj->js article)))}
     [:span {:class (if active
                      "votes active"
                      "votes")}
      (get article "net_votes")]
     [:div {:class "right"}
      [:span {:class "title"}
       (get article "title")]
      [:span {:class "worth"}
       worth]]]))

(defn list-articles [articles]
  [:div {:class "article-list"}
   (for [[index article] (map-indexed vector articles)]
     ^{:key index}
     [article-item article])])

(defn voting-power [account]
  (let [vp (voting-power-to-percent (get @account "voting_power"))]
    [:div
     [:div {:class "vp-outer"}
      [:span {:class "vp-percent"
              :style {:width (str vp "px")}}]]
     vp
     "% Voting power"]))

(defn content []
  [:div {:id "content"}
   [:div {:class "user-box"}
    [:img {:src @avatar
           :style {:width "120px"}}]
    [:div {:class "user-info"}
     [:span "@" (get @account "name")]
     [:span (get @account "balance")]
     [:span (get @account "sbd_balance")]
     [:span "Posts: " (get @account "post_count")]
     [voting-power account]]]
   [list-articles @articles]
   [:button {:on-click getDiscussions}
    "getDiscussions"]
   [:button {:on-click getAccounts}
    "getAccounts"]])

(r/render-component [content]
  (.querySelector js/document "#app"))

