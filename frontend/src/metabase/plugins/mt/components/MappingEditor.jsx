/* @flow */

import React from "react";

import Button from "metabase/components/Button";
import _ from "underscore";

type Mapping = {
  [key: string]: string,
};

type Style = {
  [key: string]: any,
};

type Props = {
  value: Mapping,
  onChange: (mapping: Mapping) => void,
  className?: string,
  style?: Style,
  keyPlaceholder?: string,
  valuePlaceholder?: string,
  keyHeader?: React.Element<any>,
  valueHeader?: React.Element<any>,
  divider?: React.Element<any>,
  canAdd?: boolean,
  canDelete?: boolean,
  swapKeyAndValue?: boolean,
};

const MappingEditor = ({
  value,
  onChange,
  className,
  style,
  keyHeader,
  valueHeader,
  keyPlaceholder = "Key",
  valuePlaceholder = "Value",
  divider,
  canAdd = true,
  canDelete = true,
  swapKeyAndValue,
}: Props) => {
  const mapping = value;
  const entries = Object.entries(mapping);
  return (
    <table className={className} style={style}>
      {keyHeader || valueHeader ? (
        <thead>
          <tr>
            <td>{!swapKeyAndValue ? keyHeader : valueHeader}</td>
            <td />
            <td>{!swapKeyAndValue ? valueHeader : keyHeader}</td>
          </tr>
        </thead>
      ) : null}
      <tbody>
        {entries.map(([key, value], index) => {
          const keyCell = (
            <input
              className="input"
              value={key}
              placeholder={keyPlaceholder}
              onChange={e =>
                onChange(replaceMappingKey(mapping, key, e.target.value))
              }
            />
          );
          const valueCell = (
            <input
              className="input"
              value={value}
              placeholder={valuePlaceholder}
              onChange={e =>
                onChange(replaceMappingValue(mapping, key, e.target.value))
              }
            />
          );

          return (
            <tr key={index}>
              <td className="pb1">{!swapKeyAndValue ? keyCell : valueCell}</td>
              <td className="pb1 px1">{divider}</td>
              <td className="pb1">{!swapKeyAndValue ? valueCell : keyCell}</td>
              {canDelete && (
                <td>
                  <Button
                    icon="close"
                    type="button" // prevent submit. should be the default but it's not
                    borderless
                    onClick={() => onChange(removeMapping(mapping, key))}
                  />
                </td>
              )}
            </tr>
          );
        })}
        {!("" in mapping) &&
          canAdd && (
            <tr>
              <td colSpan={2}>
                <Button
                  icon="add"
                  type="button" // prevent submit. should be the default but it's not
                  borderless
                  className="text-brand p0 py1"
                  onClick={() => onChange(addMapping(mapping))}
                >
                  Add an attribute
                </Button>
              </td>
            </tr>
          )}
      </tbody>
    </table>
  );
};

const addMapping = mappings => {
  return { ...mappings, "": "" };
};

const removeMapping = (mappings, prevKey) => {
  mappings = { ...mappings };
  delete mappings[prevKey];
  return mappings;
};

const replaceMappingValue = (mappings, oldKey, newValue) => {
  return { ...mappings, [oldKey]: newValue };
};

const replaceMappingKey = (mappings, oldKey, newKey) => {
  const newMappings = {};
  for (const key in mappings) {
    newMappings[key === oldKey ? newKey : key] = mappings[key];
  }
  return newMappings;
};

export default MappingEditor;
