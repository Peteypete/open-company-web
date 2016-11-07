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
            [open-company-web.lib.responsive :as responsive]))

(def link-formatter (f/formatter "yyyy-MM-dd"))
(def human-formatter (f/formatter "MMMM d, yyyy"))

(def desktop-width 500)

(defn- half-offset [pixels]
  (str "-" (Math/round (* 0.5 pixels)) "px"))

(defn update-click [link e]
  (utils/event-stop e)
  (router/nav! link))

(rum/defcs prior-updates
  < {:before-render (fn [s]
                      (when (and (dispatcher/company-data)
                                 (not (:su-list-loading @dispatcher/app-state))
                                 (not (get-in @dispatcher/app-state (dispatcher/su-list-key (router/current-company-slug)))))
                        (dispatcher/dispatch! [:get-su-list]))
                      s)}
    (drv/drv :su-list)
    (drv/drv :jwt)
    rum/reactive
  [s]
  (let [company-slug (router/current-company-slug)
        updates (reverse (drv/react s :su-list))
        none? (empty? updates)
        mobile? (responsive/is-mobile-size?)
        klass (if mobile? "" "oc-popover")] ; desktop is in a popover, mobile is full page

    [:div {:class klass
           :style {:padding 0}}
      
      (when-not mobile? ; floating close X
        [:button {:class "absolute top-0 btn-reset" :style {:left "100%"}
                  :on-click (fn [e] (popover/hide-popover e "prior-updates-dialog"))}
          (i/icon :simple-remove {:class "inline mr1" :stroke "4"
                                  :color "white" :accent-color "white"})])

      [:div.prior-updates-container {}

        [:h3.m0.gray5.domine {}
          "Prior Updates"
          (when mobile? ; inside the header close X
            [:button {:class "top-0 btn-reset" :style {:float "right" :padding-top "0" :margin-top "-5px"}
                  :on-click #(router/nav! (oc-urls/company))}
              (i/icon :simple-remove {:class "inline mr1" :stroke "4" :vertical-align "top"
                                      :color "grey" :accent-color "grey"})])]
        
        (if (empty? updates)
          
          [:div.message-container.pt2
            [:p.message "There's nothing to see here."]
            [:p.message "Start sharing!"]]

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
                    month-string (utils/month-string-int month)
                    link-date (str year "-" (utils/add-zero month) "-" (utils/add-zero day))
                    human-date (str month-string " " day ", " year)
                    update-slug (:slug update)
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
                                [:div.medium "a " (i/icon :link-72 {:size 13 :class "inline"})])
                    link-map (if mobile?
                                {:href link :on-click (partial update-click link)}
                                {:href link :target (str "_update_" update-slug)})]
                [:div.update {:key update-slug}
                  [:div.update-title.domine [:a link-map title]]
                  [:div.update-details.domine author " shared " medium " on " human-date]]))])]]))