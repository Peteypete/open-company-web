(ns oc.web.components.ui.all-caught-up
  (:require [rum.core :as rum]))

(rum/defc all-caught-up
  []
  [:div.all-caught-up
    [:div.all-caught-up-inner
      [:div.message
        "You’re all caught up!"]]])