/* @flow weak */

import React from "react";

import Radio from "metabase/components/Radio";

import _ from "underscore";

export default class AuditContent extends React.Component {
  state = {
    tabPath: null,
  };
  render() {
    const { title, subtitle, tabs, children } = this.props;
    const { tabPath } = this.state;
    const tab = _.findWhere(tabs, { path: tabPath }) || (tabs && tabs[0]);
    const TabComponent = tab && (tab.component || AuditEmptyTab);
    return (
      <div className="py4 flex flex-column flex-full">
        <div className="px4">
          <h1>{title}</h1>
          {subtitle && <div className="my1">{subtitle}</div>}
        </div>
        {tabs && (
          <div className="border-bottom px4">
            <Radio
              underlined
              options={tabs}
              value={tab && tab.path}
              onChange={tabPath => this.setState({ tabPath })}
              optionValueFn={o => o.path}
              optionNameFn={o => o.title}
              optionKeyFn={o => o.path}
            />
          </div>
        )}
        <div className="px4 full-height">
          {children}
          {TabComponent && <TabComponent {...this.props} />}
        </div>
      </div>
    );
  }
}

const AuditEmptyTab = () => (
  <div className="p4 flex layout-centered">Not yet implemented</div>
);
