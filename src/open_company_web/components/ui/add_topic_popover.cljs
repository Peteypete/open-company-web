(ns open-company-web.components.ui.add-topic-popover
  (:require [om.core :as om :include-macros true]
            [om-tools.core :as om-core :refer-macros (defcomponent)]
            [om-tools.dom :as dom :include-macros true]
            [dommy.core :as dommy :refer-macros (sel1)]
            [goog.events :as events]
            [goog.events.EventType :as EventType]
            [open-company-web.api :as api]
            [open-company-web.caches :as caches]
            [open-company-web.lib.utils :as utils]))

(defn is-child-of-popover [el]
  (loop [cur-el el]
    (if (= (.-id cur-el) "add-topic-popover")
      true
      (if (.-parentNode cur-el)
        (recur (.-parentNode cur-el))
        false))))

(defn on-click-out [owner options e]
  (when (not (is-child-of-popover (.-target e)))
    ((:dismiss-popover options))))

(defn on-click-in [owner options e]
  (.stopPropagation e))

(defn get-state [{:keys [active-topics-list all-topics]} old-state]
  (let [topics-list (map name (keys all-topics))
        unactive-topics (map name (reduce utils/vec-dissoc topics-list active-topics-list))
        unactive-equal? (= (set unactive-topics) (set (:unactive-topics old-state)))]
    {:active-topics active-topics-list
     :unactive-topics (if unactive-equal? (:unactive-topics old-state) unactive-topics)
     :highlighted-topic 0}))

(defn add-topic-click [owner options topic]
  (let [all-topics (om/get-props owner :all-topics)
        topic-data (->> topic keyword (get all-topics))
        category-name (:category topic-data)]
    ((:did-change-active-topics options) category-name topic)
    ((:dismiss-popover options))))

(defn kb-key-up [owner options e]
  (let [unactive-topics (om/get-state owner :unactive-topics)
        key-code (.-keyCode e)]
    (when (= key-code 40)
      ; down
      (.stopPropagation e)
      (.preventDefault e)
      (om/update-state! owner :highlighted-topic #(mod (inc %) (count unactive-topics))))
    (when (= key-code 38)
      ; up
      (.stopPropagation e)
      (.preventDefault e)
      (om/update-state! owner :highlighted-topic #(mod (dec %) (count unactive-topics))))
    (when (= key-code 13)
      ; enter
      (.stopPropagation e)
      (.preventDefault e)
      (let [topic (get (vec unactive-topics) (om/get-state owner :highlighted-topic))]
        (add-topic-click owner options topic)))))

(defcomponent add-topic-popover [{:keys [all-topics active-topics-list] :as data} owner options]

  (init-state [_]
    (when (empty? @caches/new-sections)
      (api/get-new-sections))
    (get-state data nil))

  (did-mount [_]
    (let [click-listener (events/listen (sel1 [:body]) EventType/CLICK (partial on-click-out owner options))
          kb-listner (events/listen (sel1 [:body]) EventType/KEYDOWN (partial kb-key-up owner options))]
      (om/set-state! owner :click-out-listener click-listener)
      (om/set-state! owner :kb-listener click-listener)))

  (will-receive-props [_ next-props]
    (when-not (= next-props data)
      (om/set-state! owner (get-state next-props (om/get-state owner)))))

  (will-unmount [_]
    (events/unlistenByKey (om/get-state owner :click-out-listener))
    (events/unlistenByKey (om/get-state owner :kb-listener)))

  (render-state [_ {:keys [active-topics unactive-topics highlighted-topic]}]
    (dom/div {:class "add-topic-popover"
              :id "add-topic-popover"
              :on-click (partial on-click-in owner options)}
      (dom/div {:class "triangle"})
      (dom/div {:class "add-topic-popover-header"} "CHOOSE A TOPIC")
      (dom/div {:class "add-topic-popover-subheader"} "SUGGESTED TOPICS")
      (dom/div {:class "topics-to-add"}
        (for [idx (range (count unactive-topics))
              :let [topic (get (vec unactive-topics) idx)
                    topic-data (->> topic keyword (get all-topics))]]
          (dom/div {:class (str "potential-topic" (when (= highlighted-topic idx) " highlighted"))
                    :on-click #(add-topic-click owner options topic)}
            (:title topic-data)))))))