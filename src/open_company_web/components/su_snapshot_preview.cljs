(ns open-company-web.components.su-snapshot-preview
  (:require [om.core :as om :include-macros true]
            [om-tools.core :as om-core :refer-macros (defcomponent)]
            [om-tools.dom :as dom :include-macros true]
            [dommy.core :as dommy :refer-macros (sel1 sel)]
            [open-company-web.api :as api]
            [open-company-web.router :as router]
            [open-company-web.dispatcher :as dis]
            [open-company-web.lib.utils :as utils]
            [open-company-web.lib.responsive :as responsive]
            [open-company-web.components.menu :refer (menu)]
            [open-company-web.components.navbar :refer (navbar)]
            [open-company-web.components.ui.back-to-dashboard-btn :refer (back-to-dashboard-btn)]
            [open-company-web.components.ui.icon :as i]
            [open-company-web.components.ui.onboard-tip :refer (onboard-tip)]
            [open-company-web.components.topics-columns :refer (topics-columns)]
            [open-company-web.components.su-preview-dialog :refer (su-preview-dialog)]
            [goog.events :as events]
            [goog.events.EventType :as EventType]
            [goog.fx.Animation.EventType :as AnimationEventType]
            [goog.fx.dom :refer (Fade)]
            [cljsjs.hammer]
            [cljsjs.react.dom]))

(defn ordered-topics-list []
  (let [topics (sel [:div.topic-row])
        topics-list (for [topic topics] (.data (js/jQuery topic) "topic"))]
    (vec (remove nil? topics-list))))

(defn post-stakeholder-update [owner]
  (om/set-state! owner :link-posting true)
  (api/share-stakeholder-update {}))

(defn stakeholder-update-data [data]
  (:stakeholder-update (dis/company-data data)))

(defn patch-stakeholder-update [owner]
  (let [title  (om/get-state owner :title)
        topics (om/get-state owner :su-topics)]
    (api/patch-stakeholder-update {:title (or title "")
                                   :sections topics})))

(defn focus-title [owner]
  (when-not (om/get-state owner :title-focused)
    (when-let [preview-title (.findDOMNode js/ReactDOM (om/get-ref owner "preview-title"))]
      (.focus preview-title)
      (set! (.-value preview-title) (.-value preview-title))
      (om/set-state! owner :title-focused true))))

(defn share-clicked [owner]
 (patch-stakeholder-update owner)
 (om/set-state! owner :show-su-dialog :prompt))

(defn dismiss-su-preview [owner]
  (om/set-state! owner (merge (om/get-state owner) {:show-su-dialog false
                                                    :slack-loading false
                                                    :link-loading false
                                                    :email-loading false
                                                    :link-posting false
                                                    :link-posted false})))

(defn setup-sortable [owner options]
  (when-let [list-node (js/jQuery (sel1 [:div.topics-column]))]
    (.sortable list-node #js {:scroll true
                              :forcePlaceholderSize true
                              :placeholder "sortable-placeholder"
                              :items ".topic-row"
                              :axis "y"
                              :handle ".topic"
                              :stop #(om/set-state! owner :su-topics (ordered-topics-list))
                              :opacity 1})))

(defn add-su-section [owner topic]
  (om/update-state! owner :su-topics #(conj % topic)))

(defn title-from-section-name [owner section]
  (let [company-data (dis/company-data (om/get-props owner))]
    (->> section keyword (get company-data) :title)))

(def topic-row-x-padding 40)

(defcomponent su-snapshot-preview [data owner options]

  (init-state [_]
    (let [company-data (dis/company-data data)
          su-data (stakeholder-update-data data)
          su-sections (if (empty? (:sections su-data))
                        (utils/filter-placeholder-sections (vec (:sections company-data)) company-data)
                        (utils/filter-placeholder-sections (:sections su-data) company-data))]
      {:columns-num (responsive/columns-num)
       :su-topics su-sections
       :title-focused false
       :title (if (clojure.string/blank? (:title su-data))  (utils/su-default-title) (:title su-data))
       :show-su-dialog false
       :link-loading false
       :slack-loading false
       :link-posting false
       :link-posted false}))

  (did-mount [_]
    (om/set-state! owner :did-mount true)
    (setup-sortable owner options)
    (events/listen js/window EventType/RESIZE #(om/set-state! owner :columns-num (responsive/columns-num)))
    (focus-title owner))

  (will-receive-props [_ next-props]
    (when-not (= (dis/company-data data) (dis/company-data next-props))
      (let [company-data (dis/company-data next-props)
            su-data      (stakeholder-update-data next-props)
            su-sections  (if (empty? (:sections su-data))
                           (vec (:sections company-data))
                           (utils/filter-placeholder-sections (:sections su-data) company-data))]
        (om/set-state! owner :su-topics su-sections)))
    ; share via link
    (when (om/get-state owner :link-loading)
      (if-not (om/get-state owner :link-posting)
        (post-stakeholder-update owner)
        ; show share url dialog
        (when (not= (dis/latest-stakeholder-update data) (dis/latest-stakeholder-update next-props))
          (om/set-state! owner :link-loading false)
          (om/set-state! owner :link-posted true)
          (om/set-state! owner :show-su-dialog true)))))

  (did-update [_ _ _]
    (focus-title owner)
    (setup-sortable owner options))

  (render-state [_ {:keys [columns-num
                           title-focused
                           title
                           show-su-dialog
                           email-loading
                           link-loading
                           slack-loading
                           link-posting
                           link-posted
                           su-topics]}]
    (let [company-slug (router/current-company-slug)
          company-data (dis/company-data data)
          su-data      (stakeholder-update-data data)
          card-width   (responsive/calc-card-width 1)
          ww           (.-clientWidth (sel1 js/document :body))
          total-width  (if (>= ww responsive/c1-min-win-width) (str (min ww (+ card-width (* topic-row-x-padding 2))) "px") "auto")
          su-subtitle  (str "— " (utils/date-string (js/Date.) [:year]))
          possible-sections (utils/filter-placeholder-sections (vec (:sections company-data)) company-data)
          topics-to-add (sort #(compare (title-from-section-name owner %1) (title-from-section-name owner %2)) (reduce utils/vec-dissoc possible-sections su-topics))]
      (dom/div {:class (utils/class-set {:su-snapshot-preview true
                                         :main-scroll true})}
        (when (and (seq company-data)
                   (empty? (:sections su-data)))
          (onboard-tip {
            :id (str "update-preview-" company-slug)
            :once-only true
            :mobile false
            :desktop "This is a preview of your update. You can drag topics to reorder, and you can remove them by clicking the \"X\"."
            :css-class "large"
            :dismiss-tip-fn #(.focus (sel1 [:input#su-snapshot-preview-title]))}))
        (om/build menu data)
        (dom/div {:class "page snapshot-page"}
          (dom/div {:class "su-snapshot-header"}
            (back-to-dashboard-btn {})
            (dom/div {:class "share-su"}
              (dom/button {:class "btn-reset btn-solid share-su-button"
                           :on-click #(share-clicked owner)
                           :disabled (zero? (count su-topics))}
                "SHARE " (dom/i {:class "fa fa-share"}))))
          ;; SU Snapshot Preview
          (when company-data
            (dom/div {:class "su-sp-content"
                      :key (apply str su-topics)}
              (dom/div {:class "su-sp-company-header"}
                (dom/div {}
                  (dom/img {:class "company-logo" :src (:logo company-data)}))
                (dom/div {}
                  (dom/span {:class "company-name"} (:name company-data))))
              (when (:title su-data)
                (dom/div {:class "preview-title-container"}
                  (dom/input #js {:className "preview-title"
                                  :id "su-snapshot-preview-title"
                                  :type "text"
                                  :value title
                                  :ref "preview-title"
                                  :onChange #(om/set-state! owner :title (.. % -target -value))
                                  :style #js {:width total-width}})))
              (dom/div {:class "preview-subtitle"} su-subtitle)
              (when show-su-dialog
                (om/build su-preview-dialog {:selected-topics (:sections su-data)
                                             :company-data company-data
                                             :latest-su (dis/latest-stakeholder-update)
                                             :share-via-slack slack-loading
                                             :share-via-email email-loading
                                             :share-via-link (or link-loading link-posted)
                                             :su-title title}
                                            {:opts {:dismiss-su-preview #(dismiss-su-preview owner)}}))
              (om/build topics-columns {:columns-num 1
                                        :card-width card-width
                                        :total-width total-width
                                        :show-fast-editing false
                                        :content-loaded (not (:loading data))
                                        :topics su-topics
                                        :company-data company-data
                                        :show-share-remove true
                                        :topics-data company-data
                                        :hide-add-topic true}
                                       {:opts {:share-remove-click (fn [topic]
                                                                      (let [fade-anim (Fade. (sel1 [(str "div#topic-" topic)]) 1 0 utils/oc-animation-duration)]
                                                                        (doto fade-anim
                                                                          (events/listen AnimationEventType/FINISH
                                                                            (fn [] (om/set-state! owner :su-topics (utils/vec-dissoc (om/get-state owner :su-topics) topic))))
                                                                          (.play))))}})))
          ;; Add section container
          (when (pos? (count topics-to-add))
            (dom/div {:class "su-preview-add-section-container"}
              (dom/div {:class "su-preview-add-section"
                        :style #js {:width total-width}}
                (dom/div {:class "add-header"} "ADD TOPICS")
                (for [topic topics-to-add
                      :let [title (->> topic keyword (get company-data) :title)]]
                  (dom/div {:class "add-section"
                            :on-click #(add-su-section owner topic)}
                    (i/icon :check-square-09 {:accent-color "transparent" :size 16 :color "black"})
                    (dom/div {:class "section-name"} title)))))))))))