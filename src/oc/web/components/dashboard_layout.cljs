(ns oc.web.components.dashboard-layout
  (:require [rum.core :as rum]
            [cuerdas.core :as s]
            [org.martinklepsch.derivatives :as drv]
            [oc.web.lib.jwt :as jwt]
            [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.cookies :as cook]
            [oc.web.utils.activity :as au]
            [oc.web.mixins.ui :as ui-mixins]
            [oc.web.actions.org :as org-actions]
            [oc.web.actions.nux :as nux-actions]
            [oc.web.lib.responsive :as responsive]
            [oc.web.actions.nav-sidebar :as nav-actions]
            [oc.web.actions.activity :as activity-actions]
            [oc.web.components.all-posts :refer (all-posts)]
            [oc.web.components.ui.empty-org :refer (empty-org)]
            [oc.web.components.ui.empty-board :refer (empty-board)]
            [oc.web.components.section-stream :refer (section-stream)]
            [oc.web.components.entries-layout :refer (entries-layout)]
            [oc.web.components.ui.dropdown-list :refer (dropdown-list)]
            [oc.web.components.navigation-sidebar :refer (navigation-sidebar)]
            [goog.events :as events]
            [goog.events.EventType :as EventType]))

(def min-scroll 50)
(def max-scroll 92)

(defn document-scroll-top []
  (if (.-body js/document)
    (max (.-pageYOffset js/window)
         (.-scrollTop (.-documentElement js/document))
         (.-scrollTop (.-body js/document)))
    0))

(defn calc-opacity [scroll-top]
  (let [fixed-scroll-top (/
                          (*
                           (- (min scroll-top max-scroll) 50)
                           100)
                          (- max-scroll min-scroll))]
    (max 0 (min (/ fixed-scroll-top 100) 1))))

(defn did-scroll [e s]
  (let [entry-floating (js/$ "#new-entry-floating-btn-container")]
    (when (pos? (.-length entry-floating))
      (let [scroll-top (document-scroll-top)
            opacity (if (responsive/is-tablet-or-mobile?)
                      1
                      (calc-opacity scroll-top))]
        (.css entry-floating #js {:opacity opacity
                                 :display (if (pos? opacity) "block" "none")}))))
  (let [dashboard-layout (rum/dom-node s)]
    (if (>= (.-scrollY js/window) 64)
      (.add (.-classList dashboard-layout) "sticky-board-name")
      (.remove (.-classList dashboard-layout) "sticky-board-name"))))

(defn get-default-section [s]
  (let [editable-boards @(drv/get-ref s :editable-boards)
        org-slug (router/current-org-slug)
        cookie-value (au/last-used-section)
        board-from-cookie (some #(when (= (:slug %) cookie-value) %) (vals editable-boards))
        filtered-boards (filterv #(not (:draft %)) (vals editable-boards))
        board-data (or board-from-cookie (first (sort-by :name filtered-boards)))]
    {:board-name (:name board-data)
     :board-slug (:slug board-data)}))

(defn get-board-for-edit [s]
  (let [board-data @(drv/get-ref s :board-data)]
    (if (or (not board-data)
            (= (:slug board-data) utils/default-drafts-board-slug))
      (get-default-section s)
      {:board-slug (:slug board-data)
       :board-name (:name board-data)})))

(defn win-width
  "Save the window width in the state."
  [s]
  (reset! (::ww s) (responsive/ww)))

(defn- update-tooltips [s]
  (when-let [$compose-button (js/$ (rum/ref-node s :top-compose-button))]
    (when (.attr $compose-button "data-original-title")
      (.tooltip $compose-button (.attr $compose-button "data-viewer"))))
  ;; Commenting out grid view switcher for now
  ; (when-let [$board-switcher (js/$ (rum/ref-node s "board-switcher"))]
  ;   (.tooltip $board-switcher)
  ;   (doto $board-switcher
  ;    (.tooltip "hide")
  ;    (.tooltip "fixTitle")))
  )

(defn compose [s]
  (utils/remove-tooltips)
  (activity-actions/activity-edit (get-board-for-edit s))
  ;; If the add post tooltip is visible
  (when @(drv/get-ref s :show-add-post-tooltip)
    ;; Dismiss it and bring up the invite people tooltip
    (utils/after 1000 nux-actions/dismiss-add-post-tooltip)))

(rum/defcs dashboard-layout < rum/reactive
                              ;; Derivative
                              (drv/drv :route)
                              (drv/drv :org-data)
                              (drv/drv :team-data)
                              (drv/drv :board-data)
                              (drv/drv :ap-initial-at)
                              (drv/drv :filtered-posts)
                              (drv/drv :editable-boards)
                              (drv/drv :show-add-post-tooltip)
                              (drv/drv :show-post-added-tooltip)
                              (drv/drv :show-draft-post-tooltip)
                              (drv/drv :mobile-navigation-sidebar)
                              (drv/drv :current-user-data)
                              ;; Locals
                              (rum/local nil ::force-update)
                              (rum/local nil ::ww)
                              (rum/local nil ::scroll-listener)
                              (rum/local nil ::show-top-boards-dropdown)
                              (rum/local nil ::show-floating-boards-dropdown)
                              ;; Commenting out grid view switcher for now
                              ; (rum/local nil ::board-switch)
                              ;; Mixins
                              (ui-mixins/render-on-resize win-width)
                              {:before-render (fn [s]
                                ;; Check if it needs any NUX stuff
                                (nux-actions/check-nux)
                                s)
                               :will-mount (fn [s]
                                (win-width s)
                                ;; Commenting out grid view switcher for now
                                ; (let [board-view-cookie (router/last-board-view-cookie (router/current-org-slug))
                                ;       cookie-value (cook/get-cookie board-view-cookie)
                                ;       board-view (or (keyword cookie-value) :stream)
                                ;       fixed-board-view (if (or (responsive/is-tablet-or-mobile?)
                                ;                                (not (nil? @(drv/get-ref s :ap-initial-at))))
                                ;                         :stream
                                ;                         board-view)]
                                ;   (reset! (::board-switch s) fixed-board-view))
                                s)
                               :did-mount (fn [s]
                                (when-not (utils/is-test-env?)
                                  (.tooltip (js/$ "[data-toggle=\"tooltip\"]"))
                                  (reset! (::scroll-listener s)
                                   (events/listen js/window EventType/SCROLL #(did-scroll % s))))
                                (update-tooltips s)
                                ;; Reopen cmail if it was open
                                (activity-actions/cmail-reopen?)
                                s)
                               :will-unmount (fn [s]
                                (when-not (utils/is-test-env?)
                                  (when @(::scroll-listener s)
                                    (events/unlistenByKey @(::scroll-listener s))
                                    (reset! (::scroll-listener s) nil)))
                                s)
                               :did-update (fn [s]
                                (update-tooltips s)
                                s)}
  [s]
  (let [org-data (drv/react s :org-data)
        board-data (drv/react s :board-data)
        posts-data (drv/react s :filtered-posts)
        route (drv/react s :route)
        team-data (drv/react s :team-data)
        is-all-posts (or (utils/in? (:route route) "all-posts")
                         (:from-all-posts route))
        is-must-see (utils/in? (:route route) "must-see")
        current-activity-id (router/current-activity-id)
        is-mobile? (responsive/is-tablet-or-mobile?)
        empty-board? (zero? (count posts-data))
        is-drafts-board (= (:slug board-data) utils/default-drafts-board-slug)
        all-boards (drv/react s :editable-boards)
        board-view-cookie (router/last-board-view-cookie (router/current-org-slug))
        drafts-board (first (filter #(= (:slug %) utils/default-drafts-board-slug) (:boards org-data)))
        drafts-link (utils/link-for (:links drafts-board) "self")
        ; board-switch (::board-switch s)
        show-drafts (pos? (:count drafts-link))
        mobile-navigation-sidebar (drv/react s :mobile-navigation-sidebar)
        can-compose (pos? (count all-boards))
        should-show-top-compose (jwt/user-is-part-of-the-team (:team-id org-data))
        current-user-data (drv/react s :current-user-data)
        is-admin-or-author (utils/is-admin-or-author? org-data)]
      ;; Entries list
      [:div.dashboard-layout.group
        [:div.dashboard-layout-container.group
          (when-not is-mobile?
            (navigation-sidebar))
          ;; Show the board always on desktop and
          ;; on mobile only when the navigation menu is not visible
          (when (or (not is-mobile?)
                    (not mobile-navigation-sidebar))
            [:div.board-container.group
              {:class (when is-all-posts "all-posts-container")}
              ;; Board name row: board name, settings button and say something button
              [:div.board-name-container.group
                {:on-click #(nav-actions/mobile-nav-sidebar)}
                ;; Board name and settings button
                [:div.board-name
                  (when (router/current-board-slug)
                    [:div.board-name-with-icon
                      [:div.board-name-with-icon-internal
                        {:class (utils/class-set {:private (= (:access board-data) "private")
                                                  :public (= (:access board-data) "public")})
                         :dangerouslySetInnerHTML (utils/emojify (cond
                                                   is-all-posts
                                                   "All posts"

                                                   is-must-see
                                                   "Must see"

                                                   :default
                                                   (:name board-data)))}]])
                  ;; Settings button
                  (when (and (router/current-board-slug)
                             (not is-all-posts)
                             (not is-must-see)
                             (not (:read-only board-data)))
                    [:div.board-settings-container.group
                      [:button.mlb-reset.board-settings-bt
                        {:data-toggle (when-not is-mobile? "tooltip")
                         :data-placement "top"
                         :data-container "body"
                         :data-delay "{\"show\":\"500\", \"hide\":\"0\"}"
                         :title (str (:name board-data) " settings")
                         :on-click #(dis/dispatch! [:input [:show-section-editor] true])}]])
                  (when (= (:access board-data) "private")
                    [:div.private-board
                      {:data-toggle "tooltip"
                       :data-placement "top"
                       :data-container "body"
                       :data-delay "{\"show\":\"500\", \"hide\":\"0\"}"
                       :title (if (= (router/current-board-slug) utils/default-drafts-board-slug)
                               "Only visible to you"
                               "Only visible to invited team members")}
                      "Private"])
                  (when (= (:access board-data) "public")
                    [:div.public-board
                      {:data-toggle "tooltip"
                       :data-placement "top"
                       :data-container "body"
                       :data-delay "{\"show\":\"500\", \"hide\":\"0\"}"
                       :title "Visible to the world, including search engines"}
                      "Public"])]
                ;; Add entry button
                (when should-show-top-compose
                  [:div.new-post-top-dropdown-container.group
                    (let [show-tooltip? (boolean (and should-show-top-compose (not can-compose)))]
                      [:button.mlb-reset.mlb-default.add-to-board-top-button.group
                        {:ref :top-compose-button
                         :on-click #(when can-compose (compose s))
                         :class (when-not can-compose "disabled")
                         :title (when show-tooltip? "You are a view-only user.")
                         :data-viewer (if show-tooltip? "enable" "disable")
                         :data-toggle (when show-tooltip? "tooltip")
                         :data-placement (when show-tooltip? "top")
                         :data-container (when show-tooltip? "body")
                         :data-delay "{\"show\":\"500\", \"hide\":\"0\"}"}
                        [:div.add-to-board-plus]
                        [:label.add-to-board-label
                          "New"]])
                    (when @(::show-top-boards-dropdown s)
                      (dropdown-list
                       {:items (map
                                #(-> %
                                  (select-keys [:name :slug])
                                  (clojure.set/rename-keys {:name :label :slug :value}))
                                (vals all-boards))
                        :value ""
                        :on-blur #(reset! (::show-top-boards-dropdown s) false)
                        :on-change (fn [item]
                                     (reset! (::show-top-boards-dropdown s) false)
                                     (activity-actions/activity-edit {:board-slug (:value item)
                                                                      :board-name (:label item)}))}))])
                ;; Commenting out grid view switcher for now
                ; (when-not is-mobile?
                ;   [:div.board-switcher.group
                ;     (let [grid-view? (= @board-switch :grid)]
                ;       [:button.mlb-reset.board-switcher-bt
                ;         {:class (if grid-view? "stream-view" "grid-view")
                ;          :ref "board-switcher"
                ;          :on-click #(do
                ;                       (reset! board-switch (if grid-view? :stream :grid))
                ;                       (cook/set-cookie! board-view-cookie (if grid-view? "stream" "grid")
                ;                        (* 60 60 24 365)))
                ;          :data-toggle "tooltip"
                ;          :data-placement "top"
                ;          :data-container "body"
                ;          :data-delay "{\"show\":\"500\", \"hide\":\"0\"}"
                ;          :title (if grid-view? "Stream view" "Grid view")}])])
                ]
              (let [add-post-tooltip (drv/react s :show-add-post-tooltip)
                    non-admin-tooltip (str "Carrot is where you'll find key announcements, updates, and "
                                           "decisions to keep you and your team pulling in the same direction.")
                    is-second-user (= add-post-tooltip :has-organic-post)]
                (when (and (not is-drafts-board)
                           add-post-tooltip)
                  [:div.add-post-tooltip-container.group
                    [:button.mlb-reset.add-post-tooltip-dismiss
                      {:on-click #(nux-actions/dismiss-add-post-tooltip)}]
                    [:div.add-post-tooltips
                      {:class (when is-second-user "second-user")}
                      [:div.add-post-tooltip-box-mobile]
                      [:div.add-post-tooltip-title
                        (str "Welcome to Carrot, " (:first-name current-user-data) "!")]
                        [:div.add-post-tooltip
                          (if is-admin-or-author
                            (if is-second-user
                              non-admin-tooltip
                              "Create a test post now to see how it works. You can delete it anytime.")
                            non-admin-tooltip)
                          (when (and is-admin-or-author
                                     (not is-second-user))
                            [:button.mlb-reset.add-post-bt
                              {:on-click #(when can-compose (compose s))}
                              "Create a new post"])]
                      [:div.add-post-tooltip-box
                        {:class (when is-second-user "second-user")}]]]))
              (when-let [add-bot-link (utils/link-for (:links team-data) "bot" "GET" {:auth-source "slack"})]
                (when (and (not is-drafts-board)
                           is-admin-or-author
                           (not is-mobile?)
                           (drv/react s :show-post-added-tooltip))
                  [:div.post-added-tooltip-container.group
                    [:button.mlb-reset.post-added-tooltip-dismiss
                      {:on-click #(do
                                   (nux-actions/dismiss-post-added-tooltip))}]
                    [:div.post-added-tooltips
                      [:div.post-added-tooltip-box-mobile]
                      [:div.post-added-tooltip-title
                        "Well done!"]
                      [:div.post-added-tooltip
                        "Using Slack? "
                        [:button.mlb-reset.post-added-bt
                          {:on-click #(org-actions/bot-auth team-data current-user-data
                                       (str (router/get-token) "?org-settings=main"))}
                          "Connect to Slack"]
                        " so your team can see posts and  join the discussion from Slack, too."]
                      [:div.post-added-tooltip-box]]]))
              (when (and is-drafts-board
                         (drv/react s :show-draft-post-tooltip))
                [:div.draft-post-tooltip-container.group
                  [:button.mlb-reset.draft-post-tooltip-dismiss
                    {:on-click #(nux-actions/dismiss-draft-post-tooltip)}]
                  [:div.draft-post-tooltips
                    [:div.draft-post-tooltip-box-mobile]
                    [:div.draft-post-tooltip-title
                      "Add some personality"]
                    [:div.draft-post-tooltip
                      "Finish this draft post to help your team get to know you better!"]
                    [:div.draft-post-tooltip-box]]])
              ;; Board content: empty org, all posts, empty board, drafts view, entries view
              (cond
                ;; No boards
                (zero? (count (:boards org-data)))
                (empty-org)
                ;; Empty board
                empty-board?
                (empty-board (when can-compose (get-board-for-edit s)))
                ;; All Posts
                (and (or is-all-posts
                         is-must-see)
                     ;; Commenting out grid view switcher for now
                     ; (= @board-switch :stream)
                     )
                (rum/with-key (all-posts)
                 (str "all-posts-component-" (if is-all-posts "AP" "MS") "-" (drv/react s :ap-initial-at)))
                ;; Layout boards activities
                :else
                (cond
                  ;; Commenting out grid view switcher for now
                  ;; Entries grid view
                  ; (= @board-switch :grid)
                  ; (entries-layout)
                  ;; Entries stream view
                  :else
                  (section-stream)))
              ;; Add entry floating button
              (when can-compose
                (let [opacity (if (responsive/is-tablet-or-mobile?)
                                1
                                (calc-opacity (document-scroll-top)))]
                  [:div.new-post-floating-dropdown-container.group
                    {:id "new-entry-floating-btn-container"
                     :style {:opacity opacity
                             :display (if (pos? opacity) "block" "none")}}
                    [:button.mlb-reset.mlb-default.add-to-board-floating-button
                      {:data-placement "left"
                       :data-container "body"
                       :data-toggle (when-not is-mobile? "tooltip")
                       :title "Start a new post"
                       :data-delay "{\"show\":\"500\", \"hide\":\"0\"}"
                       :on-click #(compose s)}
                      [:div.add-to-board-plus]]]))])]]))