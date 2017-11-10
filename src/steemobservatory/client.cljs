(ns steemobservatory.client
  (:require [reagent.core :as r]))

(defonce articles (r/atom []))
(defonce avatar (r/atom ""))
(defonce account (r/atom {}))
(defonce user-editing (r/atom false))
(defonce user-name (r/atom "crypticwyrm"))
(defonce user-name-input (r/atom ""))
(defonce show-reblogged (r/atom true))
(defonce dynamic-global-properties (r/atom {}))

(defn loadSettings []
  (if-let [storage (js/localStorage.getItem "settings")]
    (let [parsed (js/JSON.parse storage)
          settings (js->clj parsed)]
      (if-let [username (get settings "user-name")]
        (reset! user-name username))
      (if (not (nil? (get settings "show-reblogged")))
        (reset! show-reblogged (get settings "show-reblogged"))))))

(defn saveSettings []
  (if-not (empty? @user-name)
    (js/localStorage.setItem
      "settings"
      (js/JSON.stringify (clj->js {"user-name" @user-name
                                   "show-reblogged" @show-reblogged})))))

(defn parseAvatarUrl [account]
  (if (empty? (get account "json_metadata"))
    ""
    (let [parsed (js/JSON.parse (get account "json_metadata"))
          meta (js->clj parsed)]
      (get-in meta ["profile" "profile_image"]))))

(defn steemPerMvests [total_vesting_fund_steem total_vesting_shares]
  (/ total_vesting_fund_steem total_vesting_shares))

(defn vestsToSteemPower [vests steem_per_mvests]
  (* vests steem_per_mvests))

(defn vests2sp [vests]
  (vestsToSteemPower vests
                     (steemPerMvests
                       (js/parseFloat (get @dynamic-global-properties
                                           "total_vesting_fund_steem"))
                       (js/parseFloat (get @dynamic-global-properties
                                           "total_vesting_shares")))))

(defn getDynamicGlobalProperties []
  (.then
    (js/steem.database.getDynamicGlobalProperties)
    (fn [result]
      (reset! dynamic-global-properties (js->clj result))
      (js/console.log result))
    (fn [e]
      (js/console.log e))))

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
                (get article "total_payout_value"))
        reblogged (not (= (get article "author") @user-name))]
    [:div {:class (if reblogged
                    "article reblogged"
                    "article")
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
       (if reblogged
         "Reblogged: ")
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
            :style {:width "120px"
                    :height "120px"}}])
   [:div {:class "user-info"}
    (if @user-editing
      [:div
       [:input {:type "text"
                :defaultValue @user-name
                :on-change (fn [e]
                             (reset! user-name-input (-> e .-target .-value)))}]
       [:button {:on-click (fn []
                             (reset! user-editing false)
                             (reset! user-name @user-name-input)
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
       [:span
        (.toFixed (vests2sp (js/parseFloat (get @account "vesting_shares"))) 3)
        " Steem Power"]
       [voting-power account]])]])

(defn list-settings []
  [:div {:id "list-settings"}
   [:span
    [:input {:type "checkbox"
             :checked @show-reblogged
             :on-change (fn [e]
                          (reset! show-reblogged (-> e .-target .-checked))
                          (saveSettings))}]
    "Show reblogged"]])

(defn content []
  [:div {:id "content"}
   [user-box]
   [list-settings]
   [list-articles (if @show-reblogged
                    @articles
                    (filterv
                      #(= (get % "author") @user-name)
                      @articles))]])

(r/render-component [content]
  (.querySelector js/document "#app"))

(if (empty? @account)
  (do
    (loadSettings)
    (getDynamicGlobalProperties)
    (getAccounts)
    (getDiscussions)))

