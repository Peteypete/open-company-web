(ns open-company-web.components.prior-updates
  (:require [rum.core :as rum]
            [cljs-time.format :as f]
            [org.martinklepsch.derivatives :as drv]
            [open-company-web.urls :as oc-urls]
            [open-company-web.components.ui.icon :as i]
            [open-company-web.components.ui.popover :as popover]
            [open-company-web.router :as router]
            [open-company-web.dispatcher :as dispatcher]
            [open-company-web.lib.utils :as utils]
            [open-company-web.urls :as urls]
            [open-company-web.lib.responsive :as responsive]
            [open-company-web.components.ui.back-to-dashboard-btn :refer (back-to-dashboard-btn)]))

(def link-formatter (f/formatter "yyyy-MM-dd"))
(def human-formatter (f/formatter "MMMM d, yyyy"))

(def desktop-width 500)

(defn- half-offset [pixels]
  (str "-" (Math/round (* 0.5 pixels)) "px"))

(defn update-click [link e]
  (utils/event-stop e)
  (router/nav! link))

(defn load-prior-updaes-if-needed []
  (when (and (dispatcher/company-data)
             (not (:su-list-loading @dispatcher/app-state))
             (not (get-in @dispatcher/app-state (dispatcher/su-list-key (router/current-company-slug)))))
    (dispatcher/dispatch! [:get-su-list])))

(rum/defcs prior-updates
  < (drv/drv :su-list)
    (drv/drv :jwt)
    rum/reactive
  [s standalone-component current-update]
  (let [company-slug (router/current-company-slug)
        updates (reverse (drv/react s :su-list))
        none? (empty? updates)
        mobile? (responsive/is-mobile-size?)]
    (when standalone-component
      (load-prior-updaes-if-needed))
    [:div.prior-updates {:style {:padding 0}}

      (when standalone-component
        (back-to-dashboard-btn {:title "Updates"}))

      [:div.prior-updates-container {}
        
        (if (empty? updates)
          
          [:div.message-container.pt2
            [:h3.message "No company updates have been shared yet."]
            [:p.message "As updates are shared via Email, Slack and URL, they will show up here so they are always easy to find and read."]]

          [:div.update-container.pt2
            (for [update updates]
              (let [user-id (:user-id (drv/react s :jwt))
                    author-id (-> update :author :user-id)
                    same-user? (= user-id author-id)
                    iso-date (:created-at update)
                    js-date (utils/js-date iso-date)
                    month (inc (.getMonth js-date))
                    day (.getDate js-date)
                    year (.getFullYear js-date)
                    needs-year (> (- (.getTime (utils/js-date)) (.getTime js-date)) (* 1000 60 60 24 365))
                    month-string (utils/month-string-int month [:short :capitalize])
                    link-date (str year "-" (utils/add-zero month) "-" (utils/add-zero day))
                    human-date (str month-string " " day (when needs-year (str ", " year)))
                    update-slug (:slug update)
                    list-link (urls/stakeholder-update-list company-slug update-slug)
                    link-url (urls/stakeholder-update company-slug link-date update-slug)
                    link (if mobile? (str link-url "?list=true") link-url)
                    title (if (clojure.string/blank? (:title update))
                            (str (:name (dispatcher/company-data)) " Update")
                            (:title update))
                    author (if same-user? "You" (-> update :author :name))
                    medium (case (keyword (:medium update))
                                :email [:div.medium "by " (i/icon :email-84 {:size 13 :class "inline"})]
                                :slack [:span "to " [:i {:class "fa fa-slack"}]]
                                :legacy ""
                                [:div.medium "a " (i/icon :link-72 {:size 13 :class "inline"})])]
                [:div.update {:key update-slug
                              :class (when (= current-update update-slug) "active")
                              :on-click #(when-not standalone-component (update-click list-link %))}
                  [:div.update-title.domine
                    [:a {:href link :on-click #(when-not standalone-component (.preventDefault %))}
                      title]]
                  [:div.update-details.domine author " shared " medium " on " human-date]]))])]]))