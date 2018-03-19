/* eslint "react/prop-types": "warn" */
import React, { Component } from "react";
import PropTypes from "prop-types";

import FormField from "metabase/components/form/FormField.jsx";
import FormLabel from "metabase/components/form/FormLabel.jsx";
import GroupSelect from "../components/GroupSelect.jsx";
import GroupSummary from "../components/GroupSummary.jsx";
import { t } from "c-3po";
import MetabaseUtils from "metabase/lib/utils";
import SelectButton from "metabase/components/SelectButton.jsx";
import Toggle from "metabase/components/Toggle.jsx";
import PopoverWithTrigger from "metabase/components/PopoverWithTrigger.jsx";
import Button from "metabase/components/Button.jsx";
import { ModalFooter } from "metabase/components/ModalContent.jsx";

import _ from "underscore";

import { isAdminGroup, canEditMembership } from "metabase/lib/groups";

import { EDIT_USER_FORM_PLUGINS } from "metabase/plugins";

export default class EditUserForm extends Component {
  constructor(props, context) {
    super(props, context);
    this.state = {
      formError: null,
      valid: false,
      user: {
        groups: [],
        ...(props.user || {}),
      },
    };
  }

  static propTypes = {
    buttonText: PropTypes.string,
    submitFn: PropTypes.func.isRequired,
    user: PropTypes.object,
    groups: PropTypes.array,
  };

  validate = () => {
    let { valid } = this.state;
    let isValid = true;

    for (const fieldName of ["first_name", "last_name", "email"]) {
      if (MetabaseUtils.isEmpty(this.state.user[fieldName])) {
        isValid = false;
      }
    }

    if (isValid !== valid) {
      this.setState({
        valid: isValid,
      });
    }
  };

  formSubmitted(e) {
    e.preventDefault();

    this.setState({
      formError: null,
    });

    let formErrors = { data: { errors: {} } };

    // validate email address
    let email = (this.state.user.email || "").trim();
    if (!MetabaseUtils.validEmail(email)) {
      formErrors.data.errors.email = t`Not a valid formatted email address`;
    }

    if (_.keys(formErrors.data.errors).length > 0) {
      this.setState({
        formError: formErrors,
      });
      return;
    }

    this.props.submitFn({
      ...this.state.user,
      email,
    });
  }

  cancel() {
    this.props.submitFn(null);
  }

  render() {
    const { buttonText, groups } = this.props;
    const { formError, valid, user } = this.state;

    const adminGroup = _.find(groups, isAdminGroup);
    // several components expect { id: true } rather than [id]
    const selectedGroups = _.chain(user.groups)
      .map(id => [id, true])
      .object()
      .value();

    return (
      <form onSubmit={this.formSubmitted.bind(this)} noValidate>
        <div className="px4 pb2">
          <FormField fieldName="first_name" formError={formError}>
            <FormLabel
              title={t`First name`}
              fieldName="first_name"
              formError={formError}
              offset={false}
            />
            <input
              className="Form-input full"
              name="first_name"
              placeholder="Johnny"
              value={user.first_name}
              onChange={e => {
                this.setState(
                  { user: { ...user, first_name: e.target.value } },
                  this.validate,
                );
              }}
            />
          </FormField>

          <FormField fieldName="last_name" formError={formError}>
            <FormLabel
              title={t`Last name`}
              fieldName="last_name"
              formError={formError}
              offset={false}
            />
            <input
              className="Form-input full"
              name="last_name"
              placeholder="Appleseed"
              required
              value={user.last_name}
              onChange={e => {
                this.setState(
                  { user: { ...user, last_name: e.target.value } },
                  this.validate,
                );
              }}
            />
          </FormField>

          <FormField fieldName="email" formError={formError}>
            <FormLabel
              title={t`Email address`}
              fieldName="email"
              formError={formError}
              offset={false}
            />
            <input
              className="Form-input full"
              name="email"
              placeholder="youlooknicetoday@email.com"
              required
              value={user.email}
              onChange={e => {
                this.setState(
                  { user: { ...user, email: e.target.value } },
                  this.validate,
                );
              }}
            />
          </FormField>

          {groups &&
          groups.filter(g => canEditMembership(g) && !isAdminGroup(g)).length >
            0 ? (
            <FormField>
              <FormLabel title={t`Permission Groups`} offset={false} />
              <PopoverWithTrigger
                sizeToFit
                triggerElement={
                  <SelectButton>
                    <GroupSummary
                      groups={groups}
                      selectedGroups={selectedGroups}
                    />
                  </SelectButton>
                }
              >
                <GroupSelect
                  groups={groups}
                  selectedGroups={selectedGroups}
                  onGroupChange={(group, selected) => {
                    this.setState(
                      {
                        user: {
                          ...user,
                          groups: (selected
                            ? selectedGroups.add(group.id)
                            : selectedGroups.delete(group.id)
                          ).toJSON(),
                        },
                      },
                      this.validate,
                    );
                  }}
                />
              </PopoverWithTrigger>
            </FormField>
          ) : adminGroup ? (
            <div className="flex align-center">
              <Toggle
                value={selectedGroups.has(adminGroup.id)}
                onChange={isAdmin => {
                  this.setState(
                    {
                      user: { ...user, groups: isAdmin ? [adminGroup.id] : [] },
                    },
                    this.validate,
                  );
                }}
              />
              <span className="ml2">{t`Make this user an admin`}</span>
            </div>
          ) : null}
          {EDIT_USER_FORM_PLUGINS.map((Component, index) => (
            <Component
              key={index}
              user={user}
              onChange={user => this.setState({ user }, this.validate)}
            />
          ))}
        </div>

        <ModalFooter>
          <Button type="button" onClick={this.cancel.bind(this)}>
            {t`Cancel`}
          </Button>
          <Button primary disabled={!valid}>
            {buttonText ? buttonText : t`Save changes`}
          </Button>
        </ModalFooter>
      </form>
    );
  }
}
