import React, { Component } from "react";

export const withBackground = className => ComposedComponent => {
  return class extends Component {
    static displayName = "BackgroundApplicator";

    componentWillMount() {
      document.body.classList.add(className);
    }

    componentWillUnmount() {
      document.body.classList.remove(className);
    }

    render() {
      return <ComposedComponent {...this.props} />;
    }
  };
};

import { connect } from "react-redux";
import { getHasCustomLogo } from "metabase/selectors/settings";

export const withLogoBackground = ComposedComponent => {
  const mapStateToProps = (state, props) => ({
    bgClassName: getHasCustomLogo(state, props) ? "bg-brand" : "bg-white",
  });
  return connect(mapStateToProps)(
    class extends Component {
      static displayName = "BackgroundApplicator";

      componentWillMount() {
        document.body.classList.add(this.props.bgClassName);
      }

      componentWillUnmount() {
        document.body.classList.remove(this.props.bgClassName);
      }

      render() {
        // eslint-disable-next-line no-unused-vars
        const { bgClassName, ...props } = this.props;
        return <ComposedComponent {...props} />;
      }
    },
  );
};
