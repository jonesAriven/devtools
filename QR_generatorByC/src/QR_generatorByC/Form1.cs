using System;
using System.Drawing;
using System.Windows.Forms;
using QRCoder;

namespace QR_generatorByC
{
    public partial class Form1 : Form
    {
        private TextBox inputTextBox;
        private PictureBox qrPictureBox;
        private Button generateButton;

        public Form1()
        {
            InitializeComponent();
        }

        private void InitializeComponent(
