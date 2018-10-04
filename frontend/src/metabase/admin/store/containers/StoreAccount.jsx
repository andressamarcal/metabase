import React from "react";
import { Box, Flex } from "grid-styled";
import { t } from "c-3po";
import { connect } from "react-redux";

import _ from "underscore";

import colors from "metabase/lib/colors";

import StoreIcon from "../components/StoreIcon";
import Card from "metabase/components/Card";

import fitViewport from "metabase/hoc/FitViewPort";

import moment from "moment";

import FEATURES from "../lib/features";

@fitViewport
@connect(state => {
  const features = state.settings.values["premium_features"];
  const featuresEnabled = Object.keys(features).filter(
    f => features[f] === true,
  );
  return {
    features,
    featuresEnabled,
  };
})
export default class StoreAccount extends React.Component {
  render() {
    const { features, location: { query } } = this.props;
    return (
      <Flex
        align="center"
        justify="center"
        flexDirection="column"
        className={this.props.fitClassNames}
      >
        {query.state === "active" ? (
          <Active features={features} />
        ) : query.state === "expired" ? (
          <Expired features={features} />
        ) : query.state === "trial_active" ? (
          <TrialActive features={features} />
        ) : query.state === "trial_expired" ? (
          <TrialExpired features={features} />
        ) : (
          <Unlicensed />
        )}
      </Flex>
    );
  }
}

const Unlicensed = () => (
  <AccountStatus
    title={t`Get even more out of Metabase with the Enterprise Edition`}
    subtitle={
      <h4
      >{t`All the tools you need to quickly and easily provide reports for your customers, or to help you run and monitor Metabase in a large organization`}</h4>
    }
    preview
  >
    <Box m={4}>
      <a
        className="Button Button--primary"
        href={"http://metabase.com"}
      >{t`Learn more`}</a>
      <a className="Button ml2">{t`Activate a license`}</a>
    </Box>
  </AccountStatus>
);

const TrialActive = ({ features }) => (
  <AccountStatus
    title={t`Your trial is active with these features`}
    subtitle={<h3>{t`Trial expires in 14 days`}</h3>}
    features={features}
  >
    <CallToAction
      title={t`Need help? Ready to buy?`}
      buttonText={t`Talk to us`}
      buttonLink={"http://metabase.com"}
    />
  </AccountStatus>
);

const TrialExpired = ({ features }) => (
  <AccountStatus title={t`Your trial has expired`} features={features} expired>
    <CallToAction
      title={t`Need more time? Ready to buy?`}
      buttonText={t`Talk to us`}
      buttonLink={"http://metabase.com"}
    />
  </AccountStatus>
);

const Active = ({ features }) => (
  <AccountStatus
    title={t`Your features are active!`}
    subtitle={
      <h3>{t`Your licence is valid through ${moment().format(
        "MMMM d, YYYY",
      )}`}</h3>
    }
    features={features}
  />
);

const Expired = ({ features }) => (
  <AccountStatus
    title={t`Your license has expired`}
    subtitle={<h3>{t`It expired on ${moment().format("MMMM d, YYYY")}`}</h3>}
    features={features}
    expired
  >
    <CallToAction
      title={t`Want to renew your license`}
      buttonText={t`Talk to us`}
      buttonLink={"http://metabase.com"}
    />
  </AccountStatus>
);

const AccountStatus = ({
  title,
  subtitle,
  features = {},
  expired,
  preview,
  children,
  className,
}) => {
  // put included features first
  const [included, notIncluded] = _.partition(
    Object.entries(FEATURES),
    ([id, feature]) => features[id],
  );
  const featuresOrdered = [...included, ...notIncluded];
  return (
    <Flex
      align="center"
      justify="center"
      flexDirection="column"
      className={className}
      py={3}
    >
      <Box>
        <h2>{title}</h2>
      </Box>
      {subtitle && (
        <Box mt={2} color={colors["text-medium"]} style={{ maxWidth: 500 }}>
          {subtitle}
        </Box>
      )}
      <Flex mt={4} align="center">
        {featuresOrdered.map(([id, feature]) => (
          <Feature
            feature={feature}
            included={features[id]}
            expired={expired}
            preview={preview}
          />
        ))}
      </Flex>
      {children}
    </Flex>
  );
};

const CallToAction = ({ title, buttonText, buttonLink }) => (
  <Box className="rounded bg-medium m4 py3 px4 flex flex-column layout-centered">
    <h3 className="mb3">{title}</h3>
    <a className="Button Button--primary" href={buttonLink}>
      {buttonText}
    </a>
  </Box>
);

const Feature = ({ feature, included, expired, preview }) => (
  <Card
    mx={3}
    p={2}
    style={{
      opacity: expired ? 0.5 : 1,
      width: 260,
      height: 260,
      backgroundColor: included ? undefined : colors["bg-light"],
      color: included ? colors["text-dark"] : colors["text-medium"],
    }}
    className="relative flex flex-column layout-centered"
  >
    <StoreIcon
      name={feature.icon}
      color={
        preview
          ? colors["brand"]
          : included ? colors["success"] : colors["text-medium"]
      }
    />

    <Box my={2}>
      <h3 className="text-dark">{feature.name}</h3>
    </Box>

    {preview ? (
      <FeatureDescription feature={feature} />
    ) : included ? (
      <FeatureLinks
        links={feature.docs}
        defaultTitle={t`Learn how to use this`}
      />
    ) : (
      <FeatureLinks links={feature.info} defaultTitle={t`Learn more`} />
    )}

    {!included &&
      !preview && (
        <div className="spread text-centered pt2 pointer-events-none">{t`Not included in your current plan`}</div>
      )}
  </Card>
);

const FeatureDescription = ({ feature }) => (
  <div className="text-centered">{feature.description}</div>
);

const FeatureLinks = ({ links, defaultTitle }) => (
  <Flex align="center">
    {links &&
      links.map(({ link, title }) => (
        <a href={link} className="mx2 link">
          {title || defaultTitle}
        </a>
      ))}
  </Flex>
);
