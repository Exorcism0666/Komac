use crate::prompts::prompt::Prompt;
use crate::types::tag::Tag;
use derive_more::{Display, FromStr};
use serde::{Deserialize, Serialize};

#[derive(Clone, FromStr, Display, Deserialize, Serialize)]
pub struct Moniker(Tag);

impl Prompt for Moniker {
    const MESSAGE: &'static str = "Moniker:";
    const HELP_MESSAGE: Option<&'static str> = Some("Example: vscode");
    const PLACEHOLDER: Option<&'static str> = None;
}
