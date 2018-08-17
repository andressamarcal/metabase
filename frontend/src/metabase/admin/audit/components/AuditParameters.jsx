import React from "react";

import _ from "underscore";

const DEBOUNCE_PERIOD = 300;

export default class AuditParameters extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      inputValues: {},
      committedValues: {},
    };
  }

  changeValue = (key, value) => {
    this.setState({
      inputValues: { ...this.state.inputValues, [key]: value },
    });
    this.commitValueDebounced(key, value);
  };

  commitValueDebounced = _.debounce((key, value) => {
    this.setState({
      committedValues: { ...this.state.committedValues, [key]: value },
    });
  }, DEBOUNCE_PERIOD);

  render() {
    const { parameters } = this.props;
    const { inputValues, committedValues } = this.state;
    return (
      <div>
        <div className="pt4">
          {parameters.map(({ title, key, placeholder }) => (
            <input
              className="input"
              key={key}
              type="text"
              value={inputValues[key] || ""}
              placeholder={placeholder}
              onChange={e => {
                this.changeValue(key, e.target.value);
              }}
            />
          ))}
        </div>
        {this.props.children({ ...committedValues })}
      </div>
    );
  }
}
