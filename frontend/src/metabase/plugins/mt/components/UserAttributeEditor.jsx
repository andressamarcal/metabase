/* @flow */

import React from "react";

import Button from "metabase/components/Button";
import MappingEditor from "./MappingEditor";

type User = {
  first_name: string,
  last_name: string,
  login_attributes: { [key: string]: string },
};

type Props = {
  user: User,
  onChange: (user: User) => void,
};

const UserAttributeEditor = ({ user, onChange }: Props) => (
  <div className="pt2">
    <h3 className="pb1">
      {user.first_name ? `${user.first_name}'s attributes` : `Attributes`}
    </h3>
    <MappingEditor
      value={user.login_attributes || {}}
      onChange={login_attributes => onChange({ ...user, login_attributes })}
    />
  </div>
);

export default UserAttributeEditor;
