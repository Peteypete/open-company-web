(ns open-company-web.components.bw-topics-list
  (:require [om.core :as om :include-macros true]
            [om-tools.core :as om-core :refer-macros (defcomponent)]
            [om-tools.dom :as dom :include-macros true]
            [dommy.core :refer-macros (sel1 sel)]
            [open-company-web.api :as api]
            [open-company-web.urls :as oc-urls]
            [open-company-web.router :as router]
            [open-company-web.dispatcher :as dis]
            [open-company-web.lib.utils :as utils]
            [open-company-web.lib.responsive :as responsive]))

(defn patch-company [topics-list]
  (api/patch-company (router/current-board-slug) {:sections topics-list}))

(defn ordered-topics-list
  "Return the list of active topics in the order the user moved them."
  []
  (let [topics (sel [:div.left-topics-list-items :div.left-topics-list-item])
        topics-list (for [topic topics] (.data (js/jQuery topic) "topic"))]
    (vec (remove nil? topics-list))))

(defn setup-sortable
  "Setup the jQuery UI Sortable on the create-update-topics-list div"
  [owner]
  (when-let [list-node (js/jQuery "div.left-topics-list-items")]
    (-> list-node
      (.sortable #js {:scroll false
                      :forcePlaceholderSize true
                      :items ".left-topics-list-item"
                      :stop (fn [event ui]
                              (let [topics-list (ordered-topics-list)]
                                (om/set-state! owner :topics topics-list)
                                (patch-company topics-list)))
                      :axis "y"})
      (.disableSelection))))

(defn destroy-sortable
  "Destry the sortable to make sure the user doesn't DnD while looking a topic or adding a topic"
  []
  (.sortable (js/jQuery "div.left-topics-list-items") "destroy"))

(defn can-dnd? [data]
  (and (not (:read-only (:company-data data)))
       (nil? (:show-add-topic data))
       (nil? (:selected-topic-view data))))

(defn get-topics [data]
  (let [company-data (:company-data data)]
    (if (:read-only company-data)
      (utils/filter-placeholder-sections (:sections company-data) company-data)
      (:sections company-data))))

(defcomponent bw-topics-list [{:keys [company-data card-width selected-topic-view show-add-topic] :as data} owner options]

  (init-state [_]
    {:topics (get-topics data)})

  (did-mount [_]
    (when (can-dnd? data)
      (setup-sortable owner)))

  (will-update [_ next-props _]
    (om/set-state! owner :topics (get-topics next-props))
    (when (and (can-dnd? data) (not (can-dnd? next-props)))
      (destroy-sortable))
    (when (and (not (can-dnd? data)) (can-dnd? next-props))
      (setup-sortable owner)))

  (render-state [_ {:keys [topics]}]
    (dom/div {:class "left-topics-list group" :style {:width (str responsive/left-topics-list-width "px")}}
      (dom/div {:class "left-topics-list-top group"}
        (when (not= (count (:sections company-data)) 0)
          (dom/h3 {:class "left-topics-list-top-title"
                   :on-click #(when (nil? (:foce-key data))
                                (dis/dispatch! [:show-add-topic false])
                                (router/nav! (oc-urls/board)))} "TOPICS"))
        (when (and (not (responsive/is-tablet-or-mobile?))
                   (not show-add-topic)
                   (not (:read-only company-data)))
          (dom/button {:class "left-topics-list-top-title btn-reset right"
                       :on-click #(when (nil? (:foce-key data))
                                    (dis/dispatch! [:show-add-topic true]))
                       :title "Add a topic"
                       :data-placement "top"
                       :data-toggle "tooltip"
                       :data-container "body"}
            (dom/i {:class "fa fa-plus-circle"}))))
      (dom/div {:class (str "left-topics-list-items group" (when (:read-only company-data) " read-only"))}
        (for [topic topics
              :let [sd (->> topic keyword (get company-data))]]
          (dom/div {:class (utils/class-set {:left-topics-list-item true
                                             :dnd (can-dnd? data)
                                             :highlight-on-hover (nil? (:foce-key data))
                                             :group true
                                             :selected (= selected-topic-view topic)})
                    :style {:width (str (- responsive/left-topics-list-width 5) "px")}
                    :data-topic (name topic)
                    :key (str "bw-topic-list-" (name topic))
                    :on-click #(when (nil? (:foce-key data))
                                 (router/nav! (oc-urls/topic (router/current-org-slug) (router/current-board-slug) (name topic))))}
            (dom/div {:class "internal"
                      :key (str "bw-topic-list-" (name topic) "-internal")}
              (:title sd))))))))