(ns test.test-company
  (:require [doo.runner :refer-macros [doo-tests]]
            [test.open-company-web.components.company-profile]
            [test.open-company-web.components.all-sections]
            [test.open-company-web.components.revisions-navigator]
            [test.open-company-web.components.ui.company-avatar]
            [test.open-company-web.components.ui.user-avatar]
            [test.open-company-web.components.user-profile]
            [test.open-company-web.components.finances.burn-rate]
            [test.open-company-web.components.finances.cash]
            [test.open-company-web.components.finances.cash-flow]
            [test.open-company-web.components.finances.costs]
            [test.open-company-web.components.finances.revenue]
            [test.open-company-web.components.finances.runway]
            [test.open-company-web.components.finances.finances]
            [test.open-company-web.components.finances.finances-edit]
            [test.open-company-web.components.growth.growth]
            [test.open-company-web.components.growth.growth-metric]
            [test.open-company-web.components.growth.growth-edit]
            [test.open-company-web.components.growth.growth-metric-edit]
            [test.open-company-web.components.table-of-contents]
            [test.open-company-web.components.table-of-contents-item]
            [test.open-company-web.components.simple-section]
            [test.open-company-web.components.company-dashboard]
            [test.open-company-web.components.company-header]
            [test.open-company-web.components.category-nav]
            [test.open-company-web.components.topic-list]
            [test.open-company-web.components.topic]))

(enable-console-print!)

;; Break tests into somewhat logical groupings of 1/2 dozen or less tests so
;; we don't run out of memory on CI server

(doo-tests
  'test.open-company-web.components.company-profile
  'test.open-company-web.components.all-sections
  'test.open-company-web.components.revisions-navigator
  'test.open-company-web.components.ui.company-avatar
  'test.open-company-web.components.ui.user-avatar
  'test.open-company-web.components.user-profile
  'test.open-company-web.components.finances.burn-rate
  'test.open-company-web.components.finances.cash
  'test.open-company-web.components.finances.cash-flow
  'test.open-company-web.components.finances.costs
  'test.open-company-web.components.finances.revenue
  'test.open-company-web.components.finances.runway
  'test.open-company-web.components.finances.finances
  'test.open-company-web.components.finances.finances-edit
  'test.open-company-web.components.growth.growth
  'test.open-company-web.components.growth.growth-metric
  'test.open-company-web.components.growth.growth-edit
  'test.open-company-web.components.growth.growth-metric-edit
  'test.open-company-web.components.table-of-contents
  'test.open-company-web.components.table-of-contents-item
  'test.open-company-web.components.simple-section
  'test.open-company-web.components.company-dashboard
  'test.open-company-web.components.company-header
  'test.open-company-web.components.category-nav
  'test.open-company-web.components.topic-list
  'test.open-company-web.components.topic)