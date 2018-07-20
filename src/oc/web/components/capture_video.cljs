(ns oc.web.components.capture-video
  (:require [rum.core :as rum]
            [oc.web.lib.utils :as utils]
            [oc.web.dispatcher :as dis]
            [oc.web.actions.activity :as activity-actions]
            [oc.web.components.ui.ziggeo :refer (ziggeo-recorder)]))

(defn real-close [s]
  (utils/after 180 activity-actions/capture-video-dismiss))

(defn cancel-clicked [s]
  (real-close s))

(defn video-record-started [s]
  (reset! (::title s) [:div.rec [:span.recording] "Recording"]))

(defn video-record-stopped [s]
  (reset! (::title s) [:span "Pick a cover shot..."]))

(defn video-uploaded-cb [s video-token]
  (dis/dispatch! [:update [:capture-video] #(merge % {:video-id video-token
                                                      :has-changes true})]))

(rum/defcs capture-video < rum/static
                           (rum/local [:span "New video post"] ::title)
  [s]
  [:div.capture-video
    [:div.capture-video-header
      [:button.mlb-reset.modal-close-bt
        {:on-click #(cancel-clicked s)}]
      [:div.capture-video-title
        @(::title s)]]
    [:div.capture-video-body
      (ziggeo-recorder {:submit-cb video-uploaded-cb
                        :start-cb (partial video-record-started s)
                        :pick-cover-start-cb (partial video-record-stopped s)
                        :pick-cover-stop-cb nil})]])