(ns open-company-web.components.topic-list
  (:require [om.core :as om :include-macros true]
            [om-tools.core :as om-core :refer-macros [defcomponent]]
            [om-tools.dom :as dom :include-macros true]
            [open-company-web.components.topic :refer (topic)]))

(defcomponent topic-list [data owner options]
  (render [_]
    (dom/div {:class "container"}
      (dom/div {:class "topic-list"}
        (let [company-data (:company-data data)
              active-category (keyword (:active-category options))
              active-sections (get-in company-data [:sections active-category])]
        (dom/div {:class "row"}
          (str active-sections)))))))
;          (om/build topic {:topic data}))))))