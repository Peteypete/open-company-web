(ns oc.web.lib.medium-editor-exts
  (:require [oc.web.lib.utils :as utils]
            [goog.dom :as gdom]
            [goog.style :as gstyle]
            [goog.object :as gobj]
            [cljsjs.medium-editor]
            [cljsjs.rangy-selectionsaverestore]
            [org.martinklepsch.cljsjs-medium-button]))

(defn inject-extension [config-map ext-map]
  (let [n   (:name ext-map)
        ctr (js/MediumEditor.Extension.extend (clj->js ext-map))]
    (assoc-in config-map [:extensions n] (new ctr))))

(defn inject-button [config-map btn-name btn]
  (assoc-in config-map [:extensions btn-name] btn))

(def hl-btn
  (new js/MediumButton #js {:label "<i class=\"fa fa-paint-brush\"></i>" :start "<mark>" :end "</mark>"}))

(def highlight-btn
  {:name "highlight"
   :tagNames "mark"
   :contentDefault "<b>H</b>"
   :aria "Highlight"
   :action "highlight"
   :useQueryState true
   :init (fn []
           (js/rangy.init)
           (this-as this
             (js/MediumEditor.extensions.button.prototype.init.call this)
             (set! this -classApplier (js/rangy.createCssClassApplier
                                         "highlight"
                                         #js {:elementTagName "mark"
                                              :normalize true}))))
   :handleClick (fn [e] (this-as this (.. this -classApplier toggleSelection)))
})

(defn empty-paragraph? [el]
  (and (<= (-> el .-childNodes .-length) 1)
       (empty? (.-nodeValue el))
       (or (= "BR" (some-> el .-childNodes (aget 0) .-tagName))
           (empty? (some-> el .-childNodes (aget 0) .-nodeValue)))))

(def file-upload
  (let [hide-btn (fn []
                    (when-let [el (js/document.getElementById "file-upload-ui")]
                      (gstyle/setStyle el #js {:opacity 0})
                      (.remove (.-classList el) "expanded")
                      (utils/after 250 #(gstyle/setStyle el #js {:display "none"}))))
        pos-btn (fn [top-v]
                  (when-let [el (js/document.getElementById "file-upload-ui")]
                    (gstyle/setStyle el #js {:position "absolute"
                                             :display "block"
                                             :opacity 1
                                             :top (str (if (< top-v 136) (+ top-v 136) top-v) "px")
                                             :left "6px"})))
        show-btn (fn [_]
                   (utils/after 100
                    (fn []
                      (let [sel (js/window.getSelection)
                            el  (when (pos? (.-rangeCount sel))
                                  (.-commonAncestorContainer (.getRangeAt sel 0)))
                            offset-top (gobj/get el "offsetTop" 0)]
                        (when (and sel el)
                          (if (and (empty-paragraph? el) offset-top)
                            (pos-btn offset-top)
                            (hide-btn))))))
                   true)]
    {:name "file-upload"
     :init (fn []
             (this-as this
               (doseq [el (.getEditorElements this)]
                 (.on (.-base this) el "click" (.bind show-btn this))
                 (.on (.-base this) el "keyup" (.bind show-btn this)))
               (utils/after 1000 #(show-btn nil))))}))