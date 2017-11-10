(ns steemobservatory.client
  (:require [reagent.core :as r]
            [cljs.pprint :as pprint]))

(enable-console-print!)

(defonce articles (r/atom []))
(defonce avatar (r/atom ""))
(defonce account (r/atom {}))
(defonce user-editing (r/atom false))
(defonce user-name (r/atom "crypticwyrm"))

(defn loadSettings []
  (if-let [storage (js/localStorage.getItem "settings")]
    (let [parsed (js/JSON.parse storage)
          settings (js->clj parsed)]
      (if-let [username (get settings "user-name")]
        (reset! user-name username)))))

(defn saveSettings []
  (if-not (empty? @user-name)
    (js/localStorage.setItem
      "settings"
      (js/JSON.stringify (clj->js {"user-name" @user-name})))))

(defn parseAvatarUrl [account]
  (if (empty? (get account "json_metadata"))
    ""
    (let [parsed (js/JSON.parse (get account "json_metadata"))
          meta (js->clj parsed)]
      (get-in meta ["profile" "profile_image"]))))

(defn getDiscussions []
  (.then
    (js/steem.database.getDiscussions
      "blog"
      (clj->js {:limit 100
                :tag @user-name}))
    (fn [result]
      (swap! articles
        (fn []
          (map js->clj result))))
    (fn [e]
      (reset! articles [])
      (js/console.log "getDiscussions error")
      (js/console.log e))))

(defn getAccounts []
  (.then
    (js/steem.database.getAccounts (clj->js [@user-name]))
    (fn [result]
      (if (empty? result)
        (do
          (reset! account {})
          (reset! avatar "")
          (js/console.log "User doesn't exist"))
        (do
          (js/console.log "User does exist")
          (js/console.log (first result))
          (reset! account (js->clj (first result)))
          (reset! avatar
            (parseAvatarUrl
              (js->clj (first result)))))))
    (fn [e]
      (js/console.log "getAccounts error")
      (js/console.log e))))

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
      [:a {:class "title"
           :href (str "https://www.steemit.com" (get article "url"))
           :target "_blank"}
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

(defn user-box []
  [:div {:class "user-box"}
   (if (empty? @avatar)
     [:div {:id "empty-avatar"
            :style {:width "120px"
                    :height "120px"}}
      "No avatar"]
     [:img {:src @avatar
            :style {:width "120px"}}])
   [:div {:class "user-info"}
    (if @user-editing
      [:div
       [:input {:type "text"
                :defaultValue @user-name
                :on-change (fn [e]
                             (reset! user-name (-> e .-target .-value)))}]
       [:button {:on-click (fn []
                             (reset! user-editing false)
                             (saveSettings)
                             (getAccounts)
                             (getDiscussions))}
        "Ok"]]
      [:div {:id "user-name-box"}
       [:span {:id "user-name"
               :on-click (fn []
                           (reset! user-editing true))}
        "@" @user-name]
       [:span {:id "user-change"}
        "<- Click to change user"]])
    (if (empty? @account)
      [:div
       [:span "User doesn't exist, check the username"]]
      [:div {:style {:display "flex"
                     :flex-direction "column"}}
       [:span (get @account "balance")]
       [:span (get @account "sbd_balance")]
       [:span "Posts: " (get @account "post_count")]
       [voting-power account]])]])

(defn content []
  [:div {:id "content"}
   [user-box]
   [list-articles @articles]])

(r/render-component [content]
  (.querySelector js/document "#app"))

(if (empty? @account)
  (do
    (loadSettings)
    (getAccounts)
    (getDiscussions)))

