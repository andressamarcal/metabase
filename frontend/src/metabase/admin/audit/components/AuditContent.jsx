import React from "react";

import Radio from "metabase/components/Radio";

export default class AuditContent extends React.Component {
  state = {
    currentTab: null,
  };
  render() {
    const { title, tabs, children } = this.props;
    const currentTab = this.state.currentTab || (tabs && tabs[0]);
    return (
      <div className="p4 flex flex-column flex-full">
        <h1>{title}</h1>
        {tabs && (
          <div className="border-bottom">
            <Radio
              underlined
              options={tabs}
              value={currentTab}
              onChange={currentTab => this.setState({ currentTab })}
              optionValueFn={o => o}
              optionNameFn={o => o}
              optionKeyFn={o => o}
            />
          </div>
        )}
        {typeof children === "function"
          ? children({ currentTab }) || <EmptyTab />
          : children}
      </div>
    );
  }
}

const EmptyTab = () => (
  <div className="p4 flex layout-centered">Not yet implemented</div>
);
